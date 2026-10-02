//! JNI entry points.
//!
//! Every exported function is wrapped in [`std::panic::catch_unwind`] so a
//! panic in Rust (or in a dependency) becomes a thrown
//! `java.lang.RuntimeException` instead of aborting the Android process.
//! The three symbol names/signatures below are part of the Kotlin ABI and must
//! not change.

use std::panic::{AssertUnwindSafe, catch_unwind};
use std::path::{Path, PathBuf};

use jni::objects::{JByteArray, JClass, JLongArray, JObject, JObjectArray, JString};
use jni::strings::JNIString;
use jni::sys::{jboolean, jint, jlong};
use jni::{AttachGuard, Env, EnvUnowned};
use serde::Serialize;

use crate::backend;
use crate::error::{ArchiveError, Result};
use crate::format;
use crate::io_util::{CODE_CANCELLED, CODE_OK, Limits, clear_cancel, request_cancel};

fn throw(env: &mut Env, msg: impl AsRef<str>) {
    let _ = env.throw_new(
        jni::jni_str!("java/lang/RuntimeException"),
        JNIString::new(msg.as_ref()),
    );
}

fn read_string(env: &mut Env, value: &JString) -> Result<String> {
    value
        .try_to_string(env)
        .map_err(|e| ArchiveError::backend(format!("invalid Java string: {e}")))
}

struct WipedBytes(Vec<u8>);

impl Drop for WipedBytes {
    fn drop(&mut self) {
        crate::io_util::wipe_bytes(&mut self.0);
    }
}

fn read_password(env: &mut Env, value: &JByteArray) -> Result<WipedBytes> {
    env.convert_byte_array(value)
        .map(WipedBytes)
        .map_err(|e| ArchiveError::backend(format!("invalid Java byte array: {e}")))
}

fn password_str(bytes: &WipedBytes) -> Result<&str> {
    std::str::from_utf8(&bytes.0).map_err(|_| ArchiveError::invalid("password is not valid UTF-8"))
}

fn compression_options(level: jint) -> backend::CompressionOptions {
    backend::CompressionOptions {
        level: if level < 0 { None } else { Some(level) },
    }
}

fn read_sources(env: &mut Env, array: &JObjectArray) -> Result<Vec<PathBuf>> {
    let len = array
        .len(env)
        .map_err(|e| ArchiveError::backend(format!("array length: {e}")))?;
    let mut out = Vec::with_capacity(len);
    for i in 0..len {
        let element = array
            .get_element(env, i)
            .map_err(|e| ArchiveError::backend(format!("array element {i}: {e}")))?;
        if element.is_null() {
            continue;
        }
        let element: JString = env
            .cast_local::<JString>(element)
            .map_err(|e| ArchiveError::backend(format!("array element {i}: {e}")))?;
        let text = read_string(env, &element);
        env.delete_local_ref(element);
        out.push(PathBuf::from(text?));
    }
    Ok(out)
}

fn build_string_array<'local>(
    env: &mut Env<'local>,
    items: &[String],
) -> Result<JObjectArray<'local>> {
    let class = env
        .find_class(jni::jni_str!("java/lang/String"))
        .map_err(|e| ArchiveError::backend(format!("find String: {e}")))?;
    let array = env
        .new_object_array(items.len() as i32, &class, JObject::null())
        .map_err(|e| ArchiveError::backend(format!("new array: {e}")))?;
    env.delete_local_ref(class);
    for (i, item) in items.iter().enumerate() {
        let element = env
            .new_string(item)
            .map_err(|e| ArchiveError::backend(format!("new string: {e}")))?;
        array
            .set_element(env, i, &element)
            .map_err(|e| ArchiveError::backend(format!("set element: {e}")))?;
        env.delete_local_ref(element);
    }
    Ok(array)
}

fn finish_int(env: &mut Env, op: &str, outcome: std::thread::Result<Result<()>>) -> jint {
    match outcome {
        Ok(Ok(())) => CODE_OK,
        Ok(Err(ArchiveError::Cancelled)) => {
            throw(env, format!("{op} cancelled"));
            CODE_CANCELLED
        }
        Ok(Err(e)) => {
            throw(env, format!("{op} failed: {e}"));
            -1
        }
        Err(_) => {
            throw(env, format!("{op} failed: internal panic"));
            -1
        }
    }
}

fn read_strings(env: &mut Env, array: &JObjectArray) -> Result<Vec<String>> {
    let len = array
        .len(env)
        .map_err(|e| ArchiveError::backend(format!("array length: {e}")))?;
    let mut out = Vec::with_capacity(len);
    for i in 0..len {
        let element = array
            .get_element(env, i)
            .map_err(|e| ArchiveError::backend(format!("array element {i}: {e}")))?;
        if element.is_null() {
            continue;
        }
        let element: JString = env
            .cast_local::<JString>(element)
            .map_err(|e| ArchiveError::backend(format!("array element {i}: {e}")))?;
        let text = read_string(env, &element);
        env.delete_local_ref(element);
        out.push(text?);
    }
    Ok(out)
}

fn finish_void(env: &mut Env, op: &str, outcome: std::thread::Result<Result<()>>) {
    match outcome {
        Ok(Ok(())) => {}
        Ok(Err(ArchiveError::Cancelled)) => {
            throw(env, format!("{op} cancelled"));
        }
        Ok(Err(e)) => {
            throw(env, format!("{op} failed: {e}"));
        }
        Err(_) => {
            throw(env, format!("{op} failed: internal panic"));
        }
    }
}

/// `setLogFile(path: String)`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_setLogFile(
    env: EnvUnowned,
    _class: JClass,
    path: JString,
) {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let _ = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        let text = read_string(env, &path)?;
        crate::io_util::set_log_path(Some(PathBuf::from(text)));
        Ok(())
    }));
}

/// `compress(srcPaths: Array<String>, destPath: String, level: Int): Int`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_compress(
    env: EnvUnowned,
    _class: JClass,
    task_id: jlong,
    src_array: JObjectArray,
    dest_str: JString,
    level: jint,
) -> jint {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        let _guard = crate::io_util::TaskGuard::new(task_id as u64);
        let sources = read_sources(env, &src_array)?;
        let dest = PathBuf::from(read_string(env, &dest_str)?);
        let format = format::format_for_destination(&dest)?;
        backend::compress(
            &sources,
            &dest,
            format,
            &Limits::default(),
            &compression_options(level),
        )
    }));
    finish_int(env, "compress", outcome)
}

/// `extract(archivePath: String, destDir: String): Int`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_extract(
    env: EnvUnowned,
    _class: JClass,
    task_id: jlong,
    archive_str: JString,
    dest_str: JString,
) -> jint {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        let _guard = crate::io_util::TaskGuard::new(task_id as u64);
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let dest = PathBuf::from(read_string(env, &dest_str)?);
        let format = format::detect(&archive)?;
        backend::extract(&archive, &dest, format, &Limits::default())
    }));
    finish_int(env, "extract", outcome)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_extractFiltered(
    env: EnvUnowned,
    _class: JClass,
    task_id: jlong,
    archive_str: JString,
    dest_str: JString,
    names_array: JObjectArray,
) -> jint {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        let _guard = crate::io_util::TaskGuard::new(task_id as u64);
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let dest = PathBuf::from(read_string(env, &dest_str)?);
        let names = read_strings(env, &names_array)?;
        let format = format::detect(&archive)?;
        backend::extract_filtered(&archive, format, &names, &dest)
    }));
    finish_int(env, "extractFiltered", outcome)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_extractFilteredWithPassword(
    env: EnvUnowned,
    _class: JClass,
    task_id: jlong,
    archive_str: JString,
    dest_str: JString,
    names_array: JObjectArray,
    password_bytes: JByteArray,
) -> jint {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        let _guard = crate::io_util::TaskGuard::new(task_id as u64);
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let dest = PathBuf::from(read_string(env, &dest_str)?);
        let names = read_strings(env, &names_array)?;
        let password = read_password(env, &password_bytes)?;
        let format = format::detect(&archive)?;
        backend::extract_filtered_with_password(&archive, format, &names, &dest, &password.0)
    }));
    finish_int(env, "extractFilteredWithPassword", outcome)
}

/// `listArchive(archivePath: String): Array<String>`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_listArchive<'local>(
    env: EnvUnowned<'local>,
    _class: JClass<'local>,
    archive_str: JString<'local>,
) -> JObjectArray<'local> {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<Vec<String>> {
        clear_cancel();
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let format = format::detect(&archive)?;
        backend::list(&archive, format)
    }));
    match outcome {
        Ok(Ok(entries)) => match build_string_array(env, &entries) {
            Ok(array) => array,
            Err(e) => {
                throw(env, format!("listArchive failed: {e}"));
                JObjectArray::default()
            }
        },
        Ok(Err(e)) => {
            throw(env, format!("listArchive failed: {e}"));
            JObjectArray::default()
        }
        Err(_) => {
            throw(env, "listArchive failed: internal panic");
            JObjectArray::default()
        }
    }
}

/// Wire-compatible DTOs for the JSON strings consumed by Kotlin.
/// Field names (including `isDir`, `totalSize`, `passwordRequired`) are part
/// of the Kotlin ABI and must not change.
#[derive(Serialize)]
struct PreviewEntryDto<'a> {
    name: &'a str,
    size: u64,
    #[serde(rename = "isDir")]
    is_dir: bool,
    encrypted: bool,
    modified: u64,
    mode: u32,
}

#[derive(Serialize)]
struct PreviewDto<'a> {
    encrypted: bool,
    entries: Vec<PreviewEntryDto<'a>>,
}

#[derive(Serialize)]
struct TestFailureDto<'a> {
    name: &'a str,
    reason: &'a str,
}

#[derive(Serialize)]
struct TestReportDto<'a> {
    ok: bool,
    entries: usize,
    #[serde(rename = "totalSize")]
    total_size: u64,
    failures: Vec<TestFailureDto<'a>>,
    #[serde(rename = "passwordRequired")]
    password_required: bool,
}

fn preview_to_json(listing: &crate::backend::PreviewListing) -> Result<String> {
    let dto = PreviewDto {
        encrypted: listing.encrypted,
        entries: listing
            .entries
            .iter()
            .map(|e| PreviewEntryDto {
                name: &e.name,
                size: e.size,
                is_dir: e.is_dir,
                encrypted: e.encrypted,
                modified: e.modified,
                mode: e.mode,
            })
            .collect(),
    };
    serde_json::to_string(&dto).map_err(ArchiveError::backend)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_listArchiveDetailed<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: JClass<'local>,
    archive_str: JString<'local>,
) -> JString<'local> {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<String> {
        clear_cancel();
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let format = format::detect(&archive)?;
        let listing = backend::list_detailed(&archive, format)?;
        preview_to_json(&listing)
    }));
    match outcome {
        Ok(Ok(json)) => match env.new_string(json) {
            Ok(s) => s,
            Err(e) => {
                throw(env, format!("listArchiveDetailed failed: {e}"));
                JString::default()
            }
        },
        Ok(Err(e)) => {
            throw(env, format!("listArchiveDetailed failed: {e}"));
            JString::default()
        }
        Err(_) => {
            throw(env, "listArchiveDetailed failed: internal panic");
            JString::default()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_cancel(
    _env: EnvUnowned,
    _class: JClass,
) {
    request_cancel();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_cancelTask(
    _env: EnvUnowned,
    _class: JClass,
    task_id: jlong,
) {
    crate::io_util::task_request_cancel(task_id as u64);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_getProgress<'local>(
    env: EnvUnowned<'local>,
    _class: JClass<'local>,
) -> JLongArray<'local> {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let (done, total) = crate::io_util::progress_get();
    let vals = [done as jlong, total as jlong];
    match JLongArray::new(env, 2) {
        Ok(arr) => match arr.set_region(env, 0, &vals) {
            Ok(()) => arr,
            Err(_) => JLongArray::default(),
        },
        Err(_) => JLongArray::default(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_getTaskProgress<'local>(
    env: EnvUnowned<'local>,
    _class: JClass<'local>,
    task_id: jlong,
) -> JLongArray<'local> {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let (done, total) = crate::io_util::task_progress_get(task_id as u64);
    let vals = [done as jlong, total as jlong];
    match JLongArray::new(env, 2) {
        Ok(arr) => match arr.set_region(env, 0, &vals) {
            Ok(()) => arr,
            Err(_) => JLongArray::default(),
        },
        Err(_) => JLongArray::default(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_compressWithPassword(
    env: EnvUnowned,
    _class: JClass,
    task_id: jlong,
    src_array: JObjectArray,
    dest_str: JString,
    level: jint,
    password_bytes: JByteArray,
) -> jint {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        let _guard = crate::io_util::TaskGuard::new(task_id as u64);
        let sources = read_sources(env, &src_array)?;
        let dest = PathBuf::from(read_string(env, &dest_str)?);
        let password = read_password(env, &password_bytes)?;
        let pw = password_str(&password)?;
        let format = format::format_for_destination(&dest)?;
        backend::compress_with_password(
            &sources,
            &dest,
            format,
            &Limits::default(),
            pw,
            &compression_options(level),
        )
    }));
    finish_int(env, "compressWithPassword", outcome)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_extractWithPassword(
    env: EnvUnowned,
    _class: JClass,
    task_id: jlong,
    archive_str: JString,
    dest_str: JString,
    password_bytes: JByteArray,
) -> jint {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        let _guard = crate::io_util::TaskGuard::new(task_id as u64);
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let dest = PathBuf::from(read_string(env, &dest_str)?);
        let password = read_password(env, &password_bytes)?;
        let pw = password_str(&password)?;
        let format = format::detect(&archive)?;
        backend::extract_with_password(&archive, &dest, format, &Limits::default(), pw)
    }));
    finish_int(env, "extractWithPassword", outcome)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_listArchiveDetailedWithPassword<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: JClass<'local>,
    archive_str: JString<'local>,
    password_bytes: JByteArray<'local>,
) -> JString<'local> {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<String> {
        clear_cancel();
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let password = read_password(env, &password_bytes)?;
        let pw = password_str(&password)?;
        let format = format::detect(&archive)?;
        backend::list_detailed_with_password(&archive, format, pw)
            .and_then(|listing| preview_to_json(&listing))
    }));
    match outcome {
        Ok(Ok(json)) => match env.new_string(json) {
            Ok(s) => s,
            Err(e) => {
                throw(env, format!("listArchiveDetailedWithPassword failed: {e}"));
                JString::default()
            }
        },
        Ok(Err(e)) => {
            throw(env, format!("listArchiveDetailedWithPassword failed: {e}"));
            JString::default()
        }
        Err(_) => {
            throw(
                env,
                "listArchiveDetailedWithPassword failed: internal panic",
            );
            JString::default()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_testArchiveWithPassword<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: JClass<'local>,
    archive_str: JString<'local>,
    password_bytes: JByteArray<'local>,
) -> JString<'local> {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<String> {
        clear_cancel();
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let password = read_password(env, &password_bytes)?;
        let pw = password_str(&password)?;
        let format = format::detect(&archive)?;
        match backend::test_archive_with_password(&archive, format, &Limits::default(), pw) {
            Ok(report) => test_report_to_json(&report),
            Err(ArchiveError::PasswordRequired(_)) => password_required_json(),
            Err(e) => Err(e),
        }
    }));
    match outcome {
        Ok(Ok(json)) => match env.new_string(json) {
            Ok(s) => s,
            Err(e) => {
                throw(env, format!("testArchiveWithPassword failed: {e}"));
                JString::default()
            }
        },
        Ok(Err(e)) => {
            throw(env, format!("testArchiveWithPassword failed: {e}"));
            JString::default()
        }
        Err(_) => {
            throw(env, "testArchiveWithPassword failed: internal panic");
            JString::default()
        }
    }
}

fn test_report_to_json(report: &crate::backend::TestReport) -> Result<String> {
    let dto = TestReportDto {
        ok: report.ok(),
        entries: report.entries,
        total_size: report.total_size,
        failures: report
            .failures
            .iter()
            .map(|f| TestFailureDto {
                name: &f.name,
                reason: &f.reason,
            })
            .collect(),
        password_required: report.password_required,
    };
    serde_json::to_string(&dto).map_err(ArchiveError::backend)
}

fn password_required_json() -> Result<String> {
    let dto = TestReportDto {
        ok: false,
        entries: 0,
        total_size: 0,
        failures: Vec::new(),
        password_required: true,
    };
    serde_json::to_string(&dto).map_err(ArchiveError::backend)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_testArchive<'local>(
    env: EnvUnowned<'local>,
    _class: JClass<'local>,
    archive_str: JString<'local>,
) -> JString<'local> {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<String> {
        clear_cancel();
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let format = format::detect(&archive)?;
        match backend::test_archive(&archive, format, &Limits::default()) {
            Ok(report) => test_report_to_json(&report),
            Err(ArchiveError::PasswordRequired(_)) => password_required_json(),
            Err(e) => Err(e),
        }
    }));
    match outcome {
        Ok(Ok(json)) => match env.new_string(json) {
            Ok(s) => s,
            Err(e) => {
                throw(env, format!("testArchive failed: {e}"));
                JString::default()
            }
        },
        Ok(Err(e)) => {
            throw(env, format!("testArchive failed: {e}"));
            JString::default()
        }
        Err(_) => {
            throw(env, "testArchive failed: internal panic");
            JString::default()
        }
    }
}

// ---------------------------------------------------------------------------
// File-descriptor variants.
//
// Archives living in protected directories (app-private /data/data,
// Android/data) cannot be opened by path (EACCES). Kotlin passes an INT fd
// (e.g. from a Shizuku ParcelFileDescriptor) and Rust opens the magic path
// `/proc/self/fd/<N>`. Opening that path creates an INDEPENDENT file
// description, so Rust never owns or closes the caller's fd; Kotlin keeps
// ownership and closes after the call. All I/O flows through the same
// `backend::*` calls, so the existing `Limits`/cancel machinery applies
// unchanged. An invalid fd surfaces as `ArchiveError::Io` (thrown as
// RuntimeException by the JNI layer) — never a panic/unwrap.
// ---------------------------------------------------------------------------

/// Map a caller-provided fd to its magic `/proc/self/fd/<N>` path.
#[allow(non_snake_case)]
fn fdPath(fd: jint) -> PathBuf {
    PathBuf::from(format!("/proc/self/fd/{fd}"))
}

/// Shared extract core for path and fd entry points.
fn do_extract(archive: &Path, dest: &Path, password: Option<&str>) -> Result<()> {
    // Explicit probe so an invalid fd surfaces as ArchiveError::Io here. The
    // opened File is an independent description dropped immediately; the
    // caller's fd is untouched.
    std::fs::File::open(archive).map(|_| ())?;
    let format = format::detect(archive)?;
    match password {
        Some(p) => backend::extract_with_password(archive, dest, format, &Limits::default(), p),
        None => backend::extract(archive, dest, format, &Limits::default()),
    }
}

/// Shared preview core: identical JSON shape via [`preview_to_json`].
fn do_preview(archive: &Path, password: Option<&str>) -> Result<String> {
    std::fs::File::open(archive).map(|_| ())?;
    let format = format::detect(archive)?;
    let listing = match password {
        Some(p) => backend::list_detailed_with_password(archive, format, p)?,
        None => backend::list_detailed(archive, format)?,
    };
    preview_to_json(&listing)
}

/// Shared test core: identical JSON shape via [`test_report_to_json`] /
/// [`password_required_json`].
fn do_test(archive: &Path, password: Option<&str>) -> Result<String> {
    std::fs::File::open(archive).map(|_| ())?;
    let format = format::detect(archive)?;
    let report = match password {
        Some(p) => backend::test_archive_with_password(archive, format, &Limits::default(), p),
        None => backend::test_archive(archive, format, &Limits::default()),
    };
    match report {
        Ok(r) => test_report_to_json(&r),
        Err(ArchiveError::PasswordRequired(_)) => password_required_json(),
        Err(e) => Err(e),
    }
}

#[derive(Serialize)]
struct SearchMatchDto<'a> {
    name: &'a str,
    line: u64,
    snippet: &'a str,
}

#[derive(Serialize)]
struct SearchResultDto<'a> {
    matches: Vec<SearchMatchDto<'a>>,
}

fn search_to_json(matches: &[crate::content_search::ContentMatch]) -> Result<String> {
    let dto = SearchResultDto {
        matches: matches
            .iter()
            .map(|m| SearchMatchDto {
                name: &m.name,
                line: m.line,
                snippet: &m.snippet,
            })
            .collect(),
    };
    serde_json::to_string(&dto).map_err(ArchiveError::backend)
}

fn do_search(
    archive: &Path,
    needle: &str,
    case_sensitive: bool,
    password: Option<&str>,
    max_bytes: u64,
) -> Result<String> {
    std::fs::File::open(archive).map(|_| ())?;
    let format = format::detect(archive)?;
    let matches =
        backend::search_content(archive, format, needle, case_sensitive, password, max_bytes)?;
    search_to_json(&matches)
}

fn do_set_entry_meta(
    archive: &Path,
    name: &str,
    modified_millis: Option<u64>,
    mode: Option<u32>,
    password: Option<&str>,
) -> Result<()> {
    std::fs::File::open(archive).map(|_| ())?;
    let format = format::detect(archive)?;
    backend::set_entry_meta(archive, format, name, modified_millis, mode, password)
}

/// Shared JNI string-result finisher so fd and path variants throw identical
/// `RuntimeException` shapes with only the `op` label differing.
fn finish_json_string<'local>(
    env: &mut Env<'local>,
    op: &str,
    outcome: std::thread::Result<Result<String>>,
) -> JString<'local> {
    match outcome {
        Ok(Ok(json)) => match env.new_string(json) {
            Ok(s) => s,
            Err(e) => {
                throw(env, format!("{op} failed: {e}"));
                JString::default()
            }
        },
        Ok(Err(e)) => {
            throw(env, format!("{op} failed: {e}"));
            JString::default()
        }
        Err(_) => {
            throw(env, format!("{op} failed: internal panic"));
            JString::default()
        }
    }
}

/// `extractFd(fd: Int, destDir: String): Int`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_extractFd(
    env: EnvUnowned,
    _class: JClass,
    task_id: jlong,
    fd: jint,
    dest_str: JString,
) -> jint {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        let _guard = crate::io_util::TaskGuard::new(task_id as u64);
        let archive = fdPath(fd);
        let dest = PathBuf::from(read_string(env, &dest_str)?);
        do_extract(&archive, &dest, None)
    }));
    finish_int(env, "extractFd", outcome)
}

/// `extractWithPasswordFd(fd: Int, destDir: String, password: ByteArray): Int`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_extractWithPasswordFd(
    env: EnvUnowned,
    _class: JClass,
    task_id: jlong,
    fd: jint,
    dest_str: JString,
    password_bytes: JByteArray,
) -> jint {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        let _guard = crate::io_util::TaskGuard::new(task_id as u64);
        let archive = fdPath(fd);
        let dest = PathBuf::from(read_string(env, &dest_str)?);
        let password = read_password(env, &password_bytes)?;
        let pw = password_str(&password)?;
        do_extract(&archive, &dest, Some(pw))
    }));
    finish_int(env, "extractWithPasswordFd", outcome)
}

/// `listArchiveDetailedFd(fd: Int): String` (JSON preview)
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_listArchiveDetailedFd<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: JClass<'local>,
    fd: jint,
) -> JString<'local> {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<String> {
        clear_cancel();
        let archive = fdPath(fd);
        do_preview(&archive, None)
    }));
    finish_json_string(env, "listArchiveDetailedFd", outcome)
}

/// `listArchiveDetailedWithPasswordFd(fd: Int, password: ByteArray): String`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_listArchiveDetailedWithPasswordFd<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: JClass<'local>,
    fd: jint,
    password_bytes: JByteArray<'local>,
) -> JString<'local> {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<String> {
        clear_cancel();
        let archive = fdPath(fd);
        let password = read_password(env, &password_bytes)?;
        let pw = password_str(&password)?;
        do_preview(&archive, Some(pw))
    }));
    finish_json_string(env, "listArchiveDetailedWithPasswordFd", outcome)
}

/// `testArchiveFd(fd: Int): String` (JSON report)
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_testArchiveFd<'local>(
    env: EnvUnowned<'local>,
    _class: JClass<'local>,
    fd: jint,
) -> JString<'local> {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<String> {
        clear_cancel();
        let archive = fdPath(fd);
        do_test(&archive, None)
    }));
    finish_json_string(env, "testArchiveFd", outcome)
}

/// `testArchiveWithPasswordFd(fd: Int, password: ByteArray): String`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_testArchiveWithPasswordFd<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: JClass<'local>,
    fd: jint,
    password_bytes: JByteArray<'local>,
) -> JString<'local> {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<String> {
        clear_cancel();
        let archive = fdPath(fd);
        let password = read_password(env, &password_bytes)?;
        let pw = password_str(&password)?;
        do_test(&archive, Some(pw))
    }));
    finish_json_string(env, "testArchiveWithPasswordFd", outcome)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_searchArchiveContent<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: JClass<'local>,
    archive_str: JString<'local>,
    needle_str: JString<'local>,
    case_sensitive: jboolean,
    max_bytes: jlong,
    password_bytes: JByteArray<'local>,
) -> JString<'local> {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<String> {
        clear_cancel();
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let needle = read_string(env, &needle_str)?;
        let password = read_password(env, &password_bytes)?;
        let pw = if password.0.is_empty() {
            None
        } else {
            Some(password_str(&password)?)
        };
        do_search(
            &archive,
            &needle,
            case_sensitive,
            pw,
            max_bytes.max(0) as u64,
        )
    }));
    finish_json_string(env, "searchArchiveContent", outcome)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_searchArchiveContentFd<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: JClass<'local>,
    fd: jint,
    needle_str: JString<'local>,
    case_sensitive: jboolean,
    max_bytes: jlong,
    password_bytes: JByteArray<'local>,
) -> JString<'local> {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<String> {
        clear_cancel();
        let archive = fdPath(fd);
        let needle = read_string(env, &needle_str)?;
        let password = read_password(env, &password_bytes)?;
        let pw = if password.0.is_empty() {
            None
        } else {
            Some(password_str(&password)?)
        };
        do_search(
            &archive,
            &needle,
            case_sensitive,
            pw,
            max_bytes.max(0) as u64,
        )
    }));
    finish_json_string(env, "searchArchiveContentFd", outcome)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_deleteArchiveEntries(
    env: EnvUnowned,
    _class: JClass,
    archive_str: JString,
    names_array: JObjectArray,
) {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        clear_cancel();
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let names = read_strings(env, &names_array)?;
        let format = format::detect(&archive)?;
        backend::delete_entries(&archive, format, &names)
    }));
    finish_void(env, "deleteArchiveEntries", outcome)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_deleteArchiveEntriesWithPassword(
    env: EnvUnowned,
    _class: JClass,
    archive_str: JString,
    names_array: JObjectArray,
    password_bytes: JByteArray,
) {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        clear_cancel();
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let names = read_strings(env, &names_array)?;
        let password = read_password(env, &password_bytes)?;
        let format = format::detect(&archive)?;
        backend::delete_entries_with_password(&archive, format, &names, &password.0)
    }));
    finish_void(env, "deleteArchiveEntriesWithPassword", outcome)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_renameArchiveEntry(
    env: EnvUnowned,
    _class: JClass,
    archive_str: JString,
    from_str: JString,
    to_str: JString,
) {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        clear_cancel();
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let from = read_string(env, &from_str)?;
        let to = read_string(env, &to_str)?;
        let format = format::detect(&archive)?;
        backend::rename_entry(&archive, format, &from, &to)
    }));
    finish_void(env, "renameArchiveEntry", outcome)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_renameArchiveEntryWithPassword(
    env: EnvUnowned,
    _class: JClass,
    archive_str: JString,
    from_str: JString,
    to_str: JString,
    password_bytes: JByteArray,
) {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        clear_cancel();
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let from = read_string(env, &from_str)?;
        let to = read_string(env, &to_str)?;
        let password = read_password(env, &password_bytes)?;
        let format = format::detect(&archive)?;
        backend::rename_entry_with_password(&archive, format, &from, &to, &password.0)
    }));
    finish_void(env, "renameArchiveEntryWithPassword", outcome)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_addFilesToArchive(
    env: EnvUnowned,
    _class: JClass,
    archive_str: JString,
    src_array: JObjectArray,
    dest_str: JString,
) {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        clear_cancel();
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let sources = read_sources(env, &src_array)?;
        let dest_dir = read_string(env, &dest_str)?;
        let format = format::detect(&archive)?;
        backend::add_files(&archive, format, &sources, &dest_dir)
    }));
    finish_void(env, "addFilesToArchive", outcome)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_addFilesToArchiveWithPassword(
    env: EnvUnowned,
    _class: JClass,
    archive_str: JString,
    src_array: JObjectArray,
    dest_str: JString,
    password_bytes: JByteArray,
) {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        clear_cancel();
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let sources = read_sources(env, &src_array)?;
        let dest_dir = read_string(env, &dest_str)?;
        let password = read_password(env, &password_bytes)?;
        let format = format::detect(&archive)?;
        backend::add_files_with_password(&archive, format, &sources, &dest_dir, &password.0)
    }));
    finish_void(env, "addFilesToArchiveWithPassword", outcome)
}

fn meta_args(
    modified_millis: jlong,
    mode: jint,
    password: &str,
) -> (Option<u64>, Option<u32>, Option<&str>) {
    let modified = if modified_millis < 0 {
        None
    } else {
        Some(modified_millis as u64)
    };
    let mode = if mode < 0 { None } else { Some(mode as u32) };
    let pw = if password.is_empty() {
        None
    } else {
        Some(password)
    };
    (modified, mode, pw)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_setArchiveEntryMeta(
    env: EnvUnowned,
    _class: JClass,
    archive_str: JString,
    name_str: JString,
    modified_millis: jlong,
    mode: jint,
    password_bytes: JByteArray,
) {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        clear_cancel();
        let archive = PathBuf::from(read_string(env, &archive_str)?);
        let name = read_string(env, &name_str)?;
        let password = read_password(env, &password_bytes)?;
        let pw_str = password_str(&password)?;
        let (modified, mode, pw) = meta_args(modified_millis, mode, pw_str);
        do_set_entry_meta(&archive, &name, modified, mode, pw)
    }));
    finish_void(env, "setArchiveEntryMeta", outcome)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kerneldroid_karchiver_data_RustBridge_setArchiveEntryMetaFd(
    env: EnvUnowned,
    _class: JClass,
    fd: jint,
    name_str: JString,
    modified_millis: jlong,
    mode: jint,
    password_bytes: JByteArray,
) {
    let mut guard = unsafe { AttachGuard::from_unowned(env.as_raw()) };
    let env = guard.borrow_env_mut();
    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        clear_cancel();
        let archive = fdPath(fd);
        let name = read_string(env, &name_str)?;
        let password = read_password(env, &password_bytes)?;
        let pw_str = password_str(&password)?;
        let (modified, mode, pw) = meta_args(modified_millis, mode, pw_str);
        do_set_entry_meta(&archive, &name, modified, mode, pw)
    }));
    finish_void(env, "setArchiveEntryMetaFd", outcome)
}
