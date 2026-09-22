//! Path-safety, resource-limit and I/O helpers shared by every backend.
//!
//! This is the single place where "is this entry allowed to be written?" is
//! decided, so all formats get the same traversal / symlink / zip-bomb rules.

use std::cell::Cell;
use std::collections::HashMap;
use std::fs::{self, File};
use std::io::{self, BufWriter, Read, Write};
use std::path::{Component, Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::time::{SystemTime, UNIX_EPOCH};

use crate::error::{ArchiveError, CANCEL_MARKER, LIMIT_MARKER, Result};

/// Permissions applied to extracted regular files (suid/sgid/sticky stripped).
pub const FILE_MODE: u32 = 0o644;
/// Permissions applied to extracted directories (suid/sgid/sticky stripped).
pub const DIR_MODE: u32 = 0o755;

pub const CANCEL_CHECK_INTERVAL: u64 = 65536;
pub const CODE_OK: i32 = 0;
pub const CODE_CANCELLED: i32 = 2;

/// Identifier for an in-flight compress/extract task. `0` is the default task
/// used by the list/test/delete/rename/add/meta entry points.
pub type TaskId = u64;

/// Progress and cancellation state for a single task.
#[derive(Default)]
pub struct TaskState {
    done: AtomicU64,
    total: AtomicU64,
    cancel: AtomicBool,
}

impl TaskState {
    fn new() -> Self {
        Self::default()
    }

    /// Reset progress to `0/total` (does not touch the cancel flag).
    pub fn reset(&self, total: u64) {
        self.done.store(0, Ordering::Relaxed);
        self.total.store(total, Ordering::Relaxed);
    }

    /// Advance the done counter, saturating instead of wrapping.
    pub fn add(&self, n: u64) {
        let _ = self
            .done
            .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |v| {
                Some(v.saturating_add(n))
            });
    }

    /// Current `(done, total)` counters.
    pub fn get(&self) -> (u64, u64) {
        (
            self.done.load(Ordering::Relaxed),
            self.total.load(Ordering::Relaxed),
        )
    }

    /// Ask this task's worker to stop.
    pub fn request_cancel(&self) {
        self.cancel.store(true, Ordering::Relaxed);
    }

    /// Clear this task's cancellation request.
    pub fn clear_cancel(&self) {
        self.cancel.store(false, Ordering::Relaxed);
    }

    /// Whether this task has been asked to stop.
    pub fn is_cancelled(&self) -> bool {
        self.cancel.load(Ordering::Relaxed)
    }
}

static TASKS: OnceLock<Mutex<HashMap<TaskId, Arc<TaskState>>>> = OnceLock::new();

fn tasks() -> &'static Mutex<HashMap<TaskId, Arc<TaskState>>> {
    TASKS.get_or_init(|| Mutex::new(HashMap::new()))
}

/// Return the state for `id`, creating it if it does not exist yet.
pub fn task_register(id: TaskId) -> Arc<TaskState> {
    let mut map = tasks().lock().unwrap_or_else(|e| e.into_inner());
    map.entry(id)
        .or_insert_with(|| Arc::new(TaskState::new()))
        .clone()
}

/// Drop `id` from the registry. No-op if it is absent.
pub fn task_unregister(id: TaskId) {
    if let Ok(mut map) = tasks().lock() {
        map.remove(&id);
    }
}

/// Look up the state for `id`, if it exists.
pub fn task_state(id: TaskId) -> Option<Arc<TaskState>> {
    tasks().lock().ok().and_then(|map| map.get(&id).cloned())
}

/// Progress for `id`, or `(0, 0)` if the task is unknown.
pub fn task_progress_get(id: TaskId) -> (u64, u64) {
    task_state(id).map(|s| s.get()).unwrap_or((0, 0))
}

/// Request cancellation of `id`. No-op if the task is unknown.
pub fn task_request_cancel(id: TaskId) {
    if let Some(state) = task_state(id) {
        state.request_cancel();
    }
}

/// Reset `id`'s progress, registering the task if needed.
pub fn task_reset(id: TaskId, total: u64) {
    task_register(id).reset(total);
}

/// Advance `id`'s progress. No-op if the task is unknown.
pub fn task_add(id: TaskId, n: u64) {
    if let Some(state) = task_state(id) {
        state.add(n);
    }
}

thread_local! {
    static CURRENT_TASK: Cell<TaskId> = const { Cell::new(0) };
}

/// Make `id` the task the calling thread's backend code reports against.
pub fn set_current_task(id: TaskId) {
    CURRENT_TASK.with(|current| current.set(id));
}

/// The calling thread's current task, or `0` when unset.
pub fn current_task() -> TaskId {
    CURRENT_TASK.with(Cell::get)
}

/// Binds a blocking backend call to one task for the lifetime of the guard.
///
/// On creation the task is registered, made current for this thread, its
/// cancel flag is cleared and its progress reset. Dropping restores the
/// thread's current task to `0` and unregisters this task.
pub struct TaskGuard(TaskId);

impl TaskGuard {
    /// Bind the calling thread to `id`.
    pub fn new(id: TaskId) -> Self {
        let state = task_register(id);
        set_current_task(id);
        state.clear_cancel();
        state.reset(0);
        Self(id)
    }
}

impl Drop for TaskGuard {
    fn drop(&mut self) {
        set_current_task(0);
        task_unregister(self.0);
    }
}

pub fn progress_reset(total: u64) {
    task_register(current_task()).reset(total);
}

pub fn progress_add(n: u64) {
    task_add(current_task(), n);
}

pub fn progress_get() -> (u64, u64) {
    task_progress_get(current_task())
}

pub fn request_cancel() {
    task_register(current_task()).request_cancel();
}

pub fn clear_cancel() {
    if let Some(state) = task_state(current_task()) {
        state.clear_cancel();
    }
}

pub fn is_cancelled() -> bool {
    task_state(current_task())
        .map(|state| state.is_cancelled())
        .unwrap_or(false)
}

pub fn check_cancelled() -> Result<()> {
    if is_cancelled() {
        Err(ArchiveError::Cancelled)
    } else {
        Ok(())
    }
}

pub fn cancel_error() -> io::Error {
    io::Error::other(CANCEL_MARKER)
}

pub struct CancelReader<R> {
    inner: R,
    since_check: u64,
}

impl<R> CancelReader<R> {
    pub fn new(inner: R) -> Self {
        Self {
            inner,
            since_check: 0,
        }
    }
}

impl<R: Read> Read for CancelReader<R> {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        let n = self.inner.read(buf)?;
        self.since_check += n as u64;
        if self.since_check >= CANCEL_CHECK_INTERVAL {
            self.since_check = 0;
            if is_cancelled() {
                return Err(cancel_error());
            }
        }
        Ok(n)
    }
}

/// How many bytes of an archive header are inspected for magic detection.
pub const HEAD_BYTES: usize = 512;

/// Resource limits applied to every extraction. These are hard anti-abuse
/// guards, not user preferences.
#[derive(Debug, Clone)]
pub struct Limits {
    /// Maximum uncompressed size of a single entry.
    pub max_entry_size: u64,
    /// Maximum total uncompressed size of the whole archive.
    pub max_total_size: u64,
    /// Maximum number of entries in an archive.
    pub max_entries: usize,
    /// Maximum uncompressed/compressed ratio before an entry is treated as a
    /// zip bomb. Only enforced above a small floor to avoid false positives.
    pub max_compression_ratio: u64,
    /// Maximum number of bytes the engine may read from the archive stream.
    pub read_budget: u64,
}

impl Default for Limits {
    fn default() -> Self {
        Self {
            max_entry_size: 8 * 1024 * 1024 * 1024,
            max_total_size: 64 * 1024 * 1024 * 1024,
            max_entries: 200_000,
            max_compression_ratio: 1000,
            read_budget: 64 * 1024 * 1024 * 1024,
        }
    }
}

/// Mutable counters enforcing [`Limits`] across one operation.
#[derive(Debug)]
pub struct LimitState {
    limits: Limits,
    entries: usize,
    total_out: u64,
    read_remaining: u64,
}

impl LimitState {
    /// Create counters bound to the given limits.
    pub fn new(limits: &Limits) -> Self {
        Self {
            limits: limits.clone(),
            entries: 0,
            total_out: 0,
            read_remaining: limits.read_budget,
        }
    }

    /// Register a new entry and validate its declared size (if any).
    pub fn begin_entry(&mut self, name: &str, declared: Option<u64>) -> Result<()> {
        self.entries += 1;
        if self.entries > self.limits.max_entries {
            return Err(ArchiveError::limit(format!(
                "entry count {} exceeds limit {}",
                self.entries, self.limits.max_entries
            )));
        }
        if let Some(d) = declared {
            if d > self.limits.max_entry_size {
                return Err(ArchiveError::limit(format!(
                    "entry '{name}' declares {d} bytes, over per-entry limit {}",
                    self.limits.max_entry_size
                )));
            }
            if self.total_out.saturating_add(d) > self.limits.max_total_size {
                return Err(ArchiveError::limit(format!(
                    "declared output exceeds total limit {}",
                    self.limits.max_total_size
                )));
            }
        }
        Ok(())
    }

    /// Reject obviously impossible compression ratios (zip-bomb guard).
    pub fn check_ratio(&self, name: &str, compressed: u64, uncompressed: u64) -> Result<()> {
        const FLOOR: u64 = 1024 * 1024;
        if uncompressed <= FLOOR || compressed == 0 {
            return Ok(());
        }
        let ratio = uncompressed.checked_div(compressed).unwrap_or(u64::MAX);
        if ratio > self.limits.max_compression_ratio {
            return Err(ArchiveError::limit(format!(
                "entry '{name}' compression ratio {ratio} exceeds limit {}",
                self.limits.max_compression_ratio
            )));
        }
        Ok(())
    }

    /// Streaming allowance for the next entry. When the declared size is known
    /// one extra byte is allowed so that "entry larger than declared" can be
    /// detected instead of silently truncated.
    pub fn allowance(&self, declared: Option<u64>) -> u64 {
        let base = self
            .limits
            .max_entry_size
            .min(self.limits.max_total_size.saturating_sub(self.total_out))
            .min(self.read_remaining);
        match declared {
            Some(d) => base.min(d.saturating_add(1)),
            None => base,
        }
    }

    /// Record the bytes actually produced by an entry.
    pub fn finish_entry(&mut self, name: &str, declared: Option<u64>, written: u64) -> Result<()> {
        if let Some(d) = declared
            && written > d
        {
            return Err(ArchiveError::invalid(format!(
                "entry '{name}' produced {written} bytes but declared {d}"
            )));
        }
        self.total_out = self.total_out.saturating_add(written);
        if self.total_out > self.limits.max_total_size {
            return Err(ArchiveError::limit(format!(
                "total output {} exceeds limit {}",
                self.total_out, self.limits.max_total_size
            )));
        }
        self.read_remaining = self.read_remaining.saturating_sub(written);
        Ok(())
    }

    /// Bytes written so far.
    pub fn total_out(&self) -> u64 {
        self.total_out
    }
}

fn limit_error() -> io::Error {
    io::Error::other(format!("{LIMIT_MARKER}: read budget exhausted"))
}

/// A `Read` adapter that refuses to yield more than `limit` bytes.
///
/// Unlike `Read::take`, hitting the limit is an error rather than a silent
/// EOF, and the produced byte count is tracked.
pub struct LimitedReader<R> {
    inner: R,
    remaining: u64,
    count: u64,
    hit: bool,
}

impl<R: Read> LimitedReader<R> {
    /// Wrap `inner` with a byte allowance.
    pub fn new(inner: R, limit: u64) -> Self {
        Self {
            inner,
            remaining: limit,
            count: 0,
            hit: false,
        }
    }

    /// Bytes successfully read through this adapter.
    pub fn count(&self) -> u64 {
        self.count
    }

    /// True if the read budget was exhausted.
    pub fn limit_hit(&self) -> bool {
        self.hit
    }
}

impl<R: Read> Read for LimitedReader<R> {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        if is_cancelled() {
            return Err(cancel_error());
        }
        if self.remaining == 0 {
            let mut probe = [0u8; 1];
            return match self.inner.read(&mut probe) {
                Ok(0) => Ok(0),
                Ok(_) => {
                    self.hit = true;
                    Err(limit_error())
                }
                Err(e) => Err(e),
            };
        }
        let cap = (buf.len() as u64).min(self.remaining) as usize;
        let n = self.inner.read(&mut buf[..cap])?;
        self.remaining -= n as u64;
        self.count += n as u64;
        progress_add(n as u64);
        Ok(n)
    }
}

/// Normalise and validate an untrusted archive entry name.
///
/// Rejects NUL bytes, Windows drive letters / colons, absolute paths, `..`
/// components and any non-`Normal` component. Backslashes are normalised to
/// `/` so Windows-style archives cannot smuggle traversal on Unix.
fn has_windows_drive_prefix(value: &str) -> bool {
    let bytes = value.as_bytes();
    bytes.len() >= 2 && bytes[0].is_ascii_alphabetic() && bytes[1] == b':'
}

pub fn sanitize_entry_name(raw: &str) -> Result<String> {
    if raw.is_empty() {
        return Err(ArchiveError::invalid("empty entry name"));
    }
    let normalized = raw.replace('\\', "/");
    if normalized.as_bytes().contains(&0) {
        return Err(ArchiveError::security("NUL byte in entry name"));
    }
    if has_windows_drive_prefix(&normalized) {
        return Err(ArchiveError::security("drive letter in entry name"));
    }
    let mut clean = PathBuf::new();
    for component in Path::new(&normalized).components() {
        match component {
            Component::Normal(part) => clean.push(part),
            Component::CurDir => {}
            Component::ParentDir => {
                return Err(ArchiveError::security(
                    "parent directory '..' in entry name",
                ));
            }
            Component::RootDir => {
                return Err(ArchiveError::security("absolute path in entry name"));
            }
            Component::Prefix(_) => {
                return Err(ArchiveError::security("path prefix in entry name"));
            }
        }
    }
    if clean.as_os_str().is_empty() {
        return Err(ArchiveError::invalid("entry name resolves to nothing"));
    }
    Ok(clean.to_string_lossy().into_owned())
}

/// Join a validated entry name onto the destination root.
pub fn safe_join(root: &Path, entry_name: &str) -> Result<PathBuf> {
    let clean = sanitize_entry_name(entry_name)?;
    let joined = root.join(clean);
    if !joined.starts_with(root) {
        return Err(ArchiveError::security("entry escapes destination root"));
    }
    Ok(joined)
}

/// Resolve `.` and `..` purely lexically (no filesystem access).
pub fn normalize_lexically(path: &Path) -> PathBuf {
    let mut out = PathBuf::new();
    for component in path.components() {
        match component {
            Component::CurDir => {}
            Component::ParentDir => {
                out.pop();
            }
            other => out.push(other.as_os_str()),
        }
    }
    out
}

/// Validate a symlink target against the destination root.
///
/// Only relative targets that resolve inside `root` are accepted.
pub fn safe_link_target(root: &Path, link_path: &Path, target: &str) -> Result<PathBuf> {
    if target.is_empty() {
        return Err(ArchiveError::security("empty symlink target"));
    }
    if target.as_bytes().contains(&0) {
        return Err(ArchiveError::security("NUL byte in symlink target"));
    }
    let raw = Path::new(target);
    if raw.is_absolute() {
        return Err(ArchiveError::security(format!(
            "absolute symlink target: {target:?}"
        )));
    }
    let base = link_path
        .parent()
        .ok_or_else(|| ArchiveError::security("symlink has no parent directory"))?;
    let resolved = normalize_lexically(&base.join(raw));
    if !resolved.starts_with(root) {
        return Err(ArchiveError::security(format!(
            "symlink target escapes destination: {target:?}"
        )));
    }
    Ok(resolved)
}

/// Refuse to create or traverse any component (leaf included) that is already
/// a symlink. This blocks the "extract symlink, then write through it" escape.
pub fn reject_symlink_ancestors(root: &Path, target: &Path) -> Result<()> {
    let rel = target
        .strip_prefix(root)
        .map_err(|_| ArchiveError::security("path escapes destination root"))?;
    let mut cur = root.to_path_buf();
    for component in rel.components() {
        if let Component::Normal(part) = component {
            cur.push(part);
        } else {
            continue;
        }
        if let Ok(md) = fs::symlink_metadata(&cur)
            && md.file_type().is_symlink()
        {
            return Err(ArchiveError::security(format!(
                "refusing to write through symlink: {}",
                cur.display()
            )));
        }
    }
    Ok(())
}

/// Create a directory hierarchy inside `root`, refusing to pass through
/// symlinks and forcing directories to `0o755`.
pub fn create_dir_all_checked(root: &Path, dir: &Path) -> Result<()> {
    if !dir.starts_with(root) {
        return Err(ArchiveError::security("path escapes destination root"));
    }
    fs::create_dir_all(root)?;
    set_dir_mode(root)?;
    let rel = dir
        .strip_prefix(root)
        .map_err(|_| ArchiveError::security("path escapes destination root"))?;
    let mut cur = root.to_path_buf();
    for component in rel.components() {
        let Component::Normal(part) = component else {
            continue;
        };
        cur.push(part);
        match fs::symlink_metadata(&cur) {
            Ok(md) if md.file_type().is_symlink() => {
                return Err(ArchiveError::security(format!(
                    "refusing to traverse symlink: {}",
                    cur.display()
                )));
            }
            Ok(md) if md.is_dir() => set_dir_mode(&cur)?,
            Ok(_) => {
                return Err(ArchiveError::invalid(format!(
                    "path exists and is not a directory: {}",
                    cur.display()
                )));
            }
            Err(e) if e.kind() == io::ErrorKind::NotFound => {
                fs::create_dir(&cur)?;
                set_dir_mode(&cur)?;
            }
            Err(e) => return Err(e.into()),
        }
    }
    Ok(())
}

/// Create (or truncate) an output file, refusing symlinks and directories.
pub fn create_output_file(path: &Path) -> Result<File> {
    match fs::symlink_metadata(path) {
        Ok(md) if md.file_type().is_symlink() => {
            return Err(ArchiveError::security(format!(
                "refusing to overwrite symlink: {}",
                path.display()
            )));
        }
        Ok(md) if md.is_dir() => {
            return Err(ArchiveError::invalid(format!(
                "path is a directory: {}",
                path.display()
            )));
        }
        _ => {}
    }
    Ok(File::create(path)?)
}

/// Set a Unix mode, always masking off suid/sgid/sticky bits.
#[cfg(unix)]
pub fn set_mode(path: &Path, mode: u32) -> Result<()> {
    use std::os::unix::fs::PermissionsExt;
    fs::set_permissions(path, fs::Permissions::from_mode(mode & 0o777))?;
    Ok(())
}

/// No-op on non-Unix platforms.
#[cfg(not(unix))]
pub fn set_mode(_path: &Path, _mode: u32) -> Result<()> {
    Ok(())
}

/// Force `0o644` on a file.
pub fn set_file_mode(path: &Path) -> Result<()> {
    set_mode(path, FILE_MODE)
}

/// Force `0o755` on a directory.
pub fn set_dir_mode(path: &Path) -> Result<()> {
    set_mode(path, DIR_MODE)
}

static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

/// A temporary file next to the destination that is renamed into place on
/// [`AtomicFile::commit`]. Dropping without committing removes the temp file.
#[derive(Debug)]
pub struct AtomicFile {
    tmp: PathBuf,
    dest: PathBuf,
    committed: bool,
}

impl AtomicFile {
    /// Create a unique temporary path in the destination directory.
    pub fn new(dest: &Path) -> Result<Self> {
        let parent = dest
            .parent()
            .filter(|p| !p.as_os_str().is_empty())
            .unwrap_or_else(|| Path::new("."));
        fs::create_dir_all(parent)?;
        let name = dest
            .file_name()
            .ok_or_else(|| ArchiveError::invalid("destination has no file name"))?
            .to_string_lossy();
        let seq = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_nanos())
            .unwrap_or(0);
        let tmp = parent.join(format!(
            ".{}.karchiver-tmp.{}.{}.{}",
            name,
            std::process::id(),
            nanos,
            seq
        ));
        Ok(Self {
            tmp,
            dest: dest.to_path_buf(),
            committed: false,
        })
    }

    /// Path writers should target.
    pub fn path(&self) -> &Path {
        &self.tmp
    }

    /// Atomically move the temp file over the destination.
    pub fn commit(mut self) -> Result<()> {
        if let Ok(file) = File::options().write(true).open(&self.tmp) {
            file.sync_all()?;
        }
        fs::rename(&self.tmp, &self.dest)?;
        self.committed = true;
        Ok(())
    }
}

impl Drop for AtomicFile {
    fn drop(&mut self) {
        if !self.committed {
            let _ = fs::remove_file(&self.tmp);
        }
    }
}

/// Flush and unwrap a `BufWriter<File>`, returning the underlying file.
pub fn finish_bufwriter(mut writer: BufWriter<File>) -> Result<File> {
    writer.flush()?;
    writer
        .into_inner()
        .map_err(|e| ArchiveError::Io(e.into_error()))
}

/// Read at most [`HEAD_BYTES`] from the start of a file (bounded).
pub fn read_head(path: &Path) -> Result<Vec<u8>> {
    let file = File::open(path)?;
    let mut head = Vec::with_capacity(HEAD_BYTES);
    file.take(HEAD_BYTES as u64).read_to_end(&mut head)?;
    Ok(head)
}

static LOG_PATH: Mutex<Option<PathBuf>> = Mutex::new(None);

/// Route warnings to an append-only file in addition to stderr.
pub fn set_log_path(path: Option<PathBuf>) {
    if let Ok(mut guard) = LOG_PATH.lock() {
        *guard = path;
    }
}

fn now_millis() -> u128 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis())
        .unwrap_or(0)
}

/// Print a non-fatal warning (visible in logcat via stderr and in the log file).
pub fn log_warn(msg: impl std::fmt::Display) {
    let line = format!("[karchiver] {msg}");
    eprintln!("{line}");
    let path = LOG_PATH.lock().ok().and_then(|guard| guard.clone());
    if let Some(path) = path
        && let Ok(mut file) = fs::OpenOptions::new().create(true).append(true).open(&path)
    {
        let _ = writeln!(file, "{} W karchiver: {line}", now_millis());
    }
}

pub fn wipe_bytes(buf: &mut [u8]) {
    for i in 0..buf.len() {
        unsafe {
            std::ptr::write_volatile(buf.as_mut_ptr().add(i), 0);
        }
    }
    std::hint::black_box(buf.as_ptr());
}

#[cfg(test)]
mod tests {
    use super::*;

    static PROGRESS_TEST_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

    fn root() -> PathBuf {
        PathBuf::from("/tmp/karchiver-dest")
    }

    #[test]
    fn safe_join_accepts_plain_names() {
        let out = safe_join(&root(), "dir/file.txt").unwrap();
        assert_eq!(out, root().join("dir/file.txt"));
    }

    #[test]
    fn safe_join_normalises_backslashes() {
        let out = safe_join(&root(), "dir\\file.txt").unwrap();
        assert_eq!(out, root().join("dir/file.txt"));
    }

    #[test]
    fn safe_join_rejects_parent_dir() {
        assert!(matches!(
            safe_join(&root(), "../evil"),
            Err(ArchiveError::Security(_))
        ));
        assert!(matches!(
            safe_join(&root(), "a/../../evil"),
            Err(ArchiveError::Security(_))
        ));
        assert!(matches!(
            safe_join(&root(), "a\\..\\evil"),
            Err(ArchiveError::Security(_))
        ));
    }

    #[test]
    fn safe_join_rejects_absolute() {
        assert!(matches!(
            safe_join(&root(), "/etc/passwd"),
            Err(ArchiveError::Security(_))
        ));
    }

    #[test]
    fn safe_join_rejects_windows_paths() {
        assert!(matches!(
            safe_join(&root(), "C:\\Windows\\system32"),
            Err(ArchiveError::Security(_))
        ));
        assert!(matches!(
            safe_join(&root(), "\\\\?\\C:\\Windows"),
            Err(ArchiveError::Security(_))
        ));
    }

    #[test]
    fn safe_join_rejects_leading_drive_only() {
        assert!(matches!(
            safe_join(&root(), "c:relative"),
            Err(ArchiveError::Security(_))
        ));
        assert!(matches!(
            safe_join(&root(), "\\\\server\\share"),
            Err(ArchiveError::Security(_))
        ));
    }

    #[test]
    fn safe_join_accepts_colons_in_names() {
        assert_eq!(
            safe_join(&root(), "notes:2024.txt").unwrap(),
            root().join("notes:2024.txt")
        );
        assert_eq!(
            safe_join(&root(), "dir/a:b:c").unwrap(),
            root().join("dir/a:b:c")
        );
    }

    #[test]
    fn check_ratio_ignores_unknown_compressed_size() {
        let state = LimitState::new(&Limits::default());
        state
            .check_ratio("solid-continuation", 0, u64::MAX)
            .expect("zero compressed size must not look like a zip bomb");
    }

    #[test]
    fn safe_join_rejects_nul() {
        assert!(matches!(
            safe_join(&root(), "a\u{0}b"),
            Err(ArchiveError::Security(_))
        ));
        assert!(matches!(
            safe_join(&root(), ""),
            Err(ArchiveError::Invalid(_))
        ));
    }

    #[test]
    fn limited_reader_enforces_budget() {
        let _guard = PROGRESS_TEST_LOCK.lock().unwrap();
        let data = [0u8; 64];
        let mut r = LimitedReader::new(&data[..], 32);
        let mut out = Vec::new();
        let err = io::copy(&mut r, &mut out).unwrap_err();
        assert!(crate::error::classify_io(err).is_fatal());
        assert_eq!(r.count(), 32);
        assert!(r.limit_hit());
    }

    #[test]
    fn limited_reader_allows_exact_budget() {
        let _guard = PROGRESS_TEST_LOCK.lock().unwrap();
        let data = [0u8; 32];
        let mut r = LimitedReader::new(&data[..], 32);
        let mut out = Vec::new();
        io::copy(&mut r, &mut out).unwrap();
        assert_eq!(out.len(), 32);
        assert!(!r.limit_hit());
    }

    #[test]
    fn progress_reset_add_get_saturates() {
        let _guard = PROGRESS_TEST_LOCK.lock().unwrap();
        progress_reset(100);
        assert_eq!(progress_get(), (0, 100));
        progress_add(30);
        assert_eq!(progress_get(), (30, 100));
        progress_add(u64::MAX);
        assert_eq!(progress_get(), (u64::MAX, 100));
        progress_reset(0);
        assert_eq!(progress_get(), (0, 0));
    }

    #[test]
    fn limited_reader_advances_progress() {
        let _guard = PROGRESS_TEST_LOCK.lock().unwrap();
        progress_reset(1000);
        let data = [7u8; 64];
        let mut r = LimitedReader::new(std::io::Cursor::new(&data[..]), 64);
        let mut out = Vec::new();
        io::copy(&mut r, &mut out).unwrap();
        assert_eq!(out.len(), 64);
        assert_eq!(r.count(), 64);
        assert_eq!(progress_get(), (64, 1000));
        progress_reset(0);
    }

    #[test]
    fn task_progress_is_isolated_per_task() {
        let _guard = PROGRESS_TEST_LOCK.lock().unwrap();
        let a: TaskId = 0x00A1;
        let b: TaskId = 0x00B2;

        set_current_task(a);
        progress_reset(100);
        progress_add(30);

        set_current_task(b);
        progress_reset(200);
        progress_add(5);

        assert_eq!(task_progress_get(a), (30, 100));
        assert_eq!(task_progress_get(b), (5, 200));

        set_current_task(0);
        task_unregister(a);
        task_unregister(b);
    }

    #[test]
    fn task_cancel_is_isolated_and_guard_resets_current() {
        let _guard = PROGRESS_TEST_LOCK.lock().unwrap();
        let a: TaskId = 0x00A3;
        let b: TaskId = 0x00B4;

        set_current_task(a);
        task_register(a);
        set_current_task(b);
        task_register(b);

        set_current_task(b);
        task_request_cancel(a);
        assert!(!is_cancelled(), "cancelling task A must not cancel task B");

        set_current_task(a);
        assert!(is_cancelled(), "task A itself must be cancelled");
        set_current_task(0);

        assert_eq!(current_task(), 0);
        {
            let _task = TaskGuard::new(a);
            assert_eq!(current_task(), a);
            assert!(!is_cancelled(), "guard must clear the cancel flag");
            assert_eq!(progress_get(), (0, 0));
        }
        assert_eq!(current_task(), 0);

        task_unregister(b);
    }
}
