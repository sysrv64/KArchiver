//! 7-Zip backend (pure-Rust `sevenz-rust2`).

use std::collections::HashSet;
use std::fs::File;
use std::io::{self, BufReader, BufWriter, Read, Write};
use std::path::{Path, PathBuf};

use sevenz_rust2::encoder_options::{AesEncoderOptions, Lzma2Options};
use sevenz_rust2::{ArchiveEntry, ArchiveReader, ArchiveWriter, NtTime, Password, SourceReader};

use crate::backend::collect_sources;
use crate::backend::{
    ContentMatch, PreviewEntry, PreviewListing, SourceEntry, TestFailure, TestReport,
};
use crate::content_search::Scanner;
use crate::error::{ArchiveError, CANCEL_MARKER, LIMIT_MARKER, Result, classify_io};
use crate::io_util::{
    AtomicFile, CancelReader, LimitState, LimitedReader, Limits, check_cancelled,
    create_dir_all_checked, create_output_file, is_cancelled, log_warn, progress_add,
    progress_reset, reject_symlink_ancestors, safe_join, sanitize_entry_name, set_file_mode,
};

fn map_err(e: sevenz_rust2::Error) -> ArchiveError {
    if let sevenz_rust2::Error::Io(io_err, _) = &e {
        let msg = io_err.to_string();
        if msg.contains(CANCEL_MARKER) {
            return ArchiveError::Cancelled;
        }
        if msg.contains(LIMIT_MARKER) {
            return ArchiveError::limit(e);
        }
    }
    ArchiveError::backend(e)
}

fn map_open_err(e: sevenz_rust2::Error) -> ArchiveError {
    map_open_err_pw(e, false)
}

fn map_open_err_pw(e: sevenz_rust2::Error, have_password: bool) -> ArchiveError {
    match &e {
        sevenz_rust2::Error::PasswordRequired => {
            ArchiveError::password_required("archive header is encrypted")
        }
        sevenz_rust2::Error::MaybeBadPassword(_) => {
            if have_password {
                ArchiveError::wrong_password("incorrect password")
            } else {
                ArchiveError::password_required("archive requires a password")
            }
        }
        _ => map_err(e),
    }
}

fn password_of(password: &str) -> (Password, bool) {
    if password.is_empty() {
        (Password::empty(), false)
    } else {
        (Password::new(password), true)
    }
}

pub fn compress(sources: &[PathBuf], dest: &Path, limits: &Limits) -> Result<()> {
    compress_impl(sources, dest, limits, None)
}

pub fn compress_with_password(
    sources: &[PathBuf],
    dest: &Path,
    limits: &Limits,
    password: &str,
) -> Result<()> {
    if password.is_empty() {
        return compress_impl(sources, dest, limits, None);
    }
    compress_impl(sources, dest, limits, Some(password))
}

const SOLID_MAX_FILES: usize = 128;
const SOLID_MAX_BYTES: u64 = 16 * 1024 * 1024;
const READ_BUF_CAP: usize = 65536;
const MT_CHUNK_BYTES: u64 = 4 * 1024 * 1024;

fn lzma2_options() -> Lzma2Options {
    let threads = std::thread::available_parallelism()
        .map(|n| n.get() as u32)
        .unwrap_or(2)
        .clamp(1, 8);
    Lzma2Options::from_level_mt(5, threads, MT_CHUNK_BYTES)
}

fn flush_solid_batch(
    writer: &mut ArchiveWriter<File>,
    entries: &mut Vec<ArchiveEntry>,
    readers: &mut Vec<SourceReader<Box<dyn Read>>>,
    meta: &mut Vec<(String, u64)>,
    state: &mut LimitState,
) -> Result<()> {
    if entries.is_empty() {
        return Ok(());
    }
    writer
        .push_archive_entries(std::mem::take(entries), std::mem::take(readers))
        .map_err(map_err)?;
    for (name, declared) in std::mem::take(meta) {
        state.finish_entry(&name, Some(declared), declared)?;
    }
    Ok(())
}

fn compress_impl(
    sources: &[PathBuf],
    dest: &Path,
    limits: &Limits,
    password: Option<&str>,
) -> Result<()> {
    let af = AtomicFile::new(dest)?;
    let entries = collect_sources(sources, &[dest, af.path()], limits)?;
    let mut state = LimitState::new(limits);
    let mut writer = ArchiveWriter::create(af.path()).map_err(map_err)?;
    if let Some(pw) = password {
        writer.set_content_methods(vec![
            AesEncoderOptions::new(Password::new(pw)).into(),
            lzma2_options().into(),
        ]);
    } else {
        writer.set_content_methods(vec![lzma2_options().into()]);
    }

    let mut batch_entries: Vec<ArchiveEntry> = Vec::new();
    let mut batch_readers: Vec<SourceReader<Box<dyn Read>>> = Vec::new();
    let mut batch_meta: Vec<(String, u64)> = Vec::new();
    let mut batch_bytes: u64 = 0;

    for e in &entries {
        check_cancelled()?;
        if e.is_dir {
            flush_solid_batch(
                &mut writer,
                &mut batch_entries,
                &mut batch_readers,
                &mut batch_meta,
                &mut state,
            )?;
            batch_bytes = 0;
            let entry = ArchiveEntry::new_directory(&e.name);
            writer
                .push_archive_entry::<io::Empty>(entry, None)
                .map_err(map_err)?;
            continue;
        }
        state.begin_entry(&e.name, Some(e.size))?;
        let entry = ArchiveEntry::from_path(&e.path, e.name.clone());
        let allowance = state.allowance(Some(e.size));
        let reader: Box<dyn Read> = if e.size == 0 {
            Box::new(io::empty())
        } else {
            Box::new(LimitedReader::new(
                BufReader::with_capacity(READ_BUF_CAP, File::open(&e.path)?),
                allowance,
            ))
        };
        batch_bytes = batch_bytes.saturating_add(e.size);
        batch_entries.push(entry);
        batch_readers.push(SourceReader::new(reader));
        batch_meta.push((e.name.clone(), e.size));
        if batch_entries.len() >= SOLID_MAX_FILES || batch_bytes >= SOLID_MAX_BYTES {
            flush_solid_batch(
                &mut writer,
                &mut batch_entries,
                &mut batch_readers,
                &mut batch_meta,
                &mut state,
            )?;
            batch_bytes = 0;
        }
    }
    flush_solid_batch(
        &mut writer,
        &mut batch_entries,
        &mut batch_readers,
        &mut batch_meta,
        &mut state,
    )?;

    writer.finish()?;
    af.commit()
}

fn extract_entry(
    entry: &ArchiveEntry,
    data: &mut dyn Read,
    root: &Path,
    state: &mut LimitState,
) -> Result<()> {
    check_cancelled()?;
    let name = entry.name().to_string();
    if entry.is_directory() {
        state.begin_entry(&name, Some(0))?;
        let out = safe_join(root, &name)?;
        create_dir_all_checked(root, &out)?;
        return Ok(());
    }

    let declared = entry.size();
    let out = safe_join(root, &name)?;
    let parent = out
        .parent()
        .ok_or_else(|| ArchiveError::invalid("entry has no parent"))?;
    create_dir_all_checked(root, parent)?;
    reject_symlink_ancestors(root, &out)?;

    state.begin_entry(&name, Some(declared))?;
    let file = create_output_file(&out)?;
    let mut partial = true;
    let result = (|| -> Result<()> {
        let mut writer = BufWriter::new(file);
        let allowance = state.allowance(Some(declared));
        let mut limited = LimitedReader::new(data, allowance);
        io::copy(&mut limited, &mut writer).map_err(classify_io)?;
        writer.flush()?;
        drop(writer);
        set_file_mode(&out)?;
        state.finish_entry(&name, Some(declared), limited.count())?;
        partial = false;
        Ok(())
    })();
    if partial {
        let _ = std::fs::remove_file(&out);
    }
    result
}

/// Extract a 7z archive into `dest`.
pub fn extract(archive: &Path, dest: &Path, limits: &Limits) -> Result<()> {
    extract_impl(archive, dest, limits, None, None)
}

pub fn extract_with_password(
    archive: &Path,
    dest: &Path,
    limits: &Limits,
    password: &str,
) -> Result<()> {
    if password.is_empty() {
        return extract_impl(archive, dest, limits, None, None);
    }
    extract_impl(archive, dest, limits, Some(password), None)
}

pub fn extract_filtered(
    archive: &Path,
    dest: &Path,
    limits: &Limits,
    names: &[String],
) -> Result<()> {
    let filters = crate::backend::normalize_filter_names(names)?;
    if filters.is_empty() {
        return Ok(());
    }
    extract_impl(archive, dest, limits, None, Some(&filters))
}

pub fn extract_filtered_with_password(
    archive: &Path,
    dest: &Path,
    limits: &Limits,
    names: &[String],
    password: &str,
) -> Result<()> {
    let filters = crate::backend::normalize_filter_names(names)?;
    if filters.is_empty() {
        return Ok(());
    }
    if password.is_empty() {
        return extract_impl(archive, dest, limits, None, Some(&filters));
    }
    extract_impl(archive, dest, limits, Some(password), Some(&filters))
}

fn extract_impl(
    archive: &Path,
    dest: &Path,
    limits: &Limits,
    password: Option<&str>,
    filter: Option<&[String]>,
) -> Result<()> {
    std::fs::create_dir_all(dest)?;
    let dest_root = std::fs::canonicalize(dest)?;
    let (pw, have_password) = match password {
        Some(p) => password_of(p),
        None => (Password::empty(), false),
    };
    let mut reader =
        ArchiveReader::open(archive, pw).map_err(|e| map_open_err_pw(e, have_password))?;
    let total = reader
        .archive()
        .files
        .iter()
        .filter(|f| !f.is_directory())
        .filter(|f| filter.is_none_or(|flt| crate::backend::filter_matches(f.name(), flt)))
        .fold(0u64, |acc, f| acc.saturating_add(f.size()));
    progress_reset(total);
    let mut state = LimitState::new(limits);
    let mut warnings: Vec<String> = Vec::new();
    let mut fatal: Option<ArchiveError> = None;

    let result = reader.for_each_entries(|entry, data| {
        if is_cancelled() {
            fatal = Some(ArchiveError::Cancelled);
            return Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()));
        }
        if fatal.is_some() {
            return Ok(true);
        }
        let name = entry.name().to_string();
        if filter.is_some_and(|f| !crate::backend::filter_matches(&name, f)) {
            let mut cancellable = CancelReader::new(&mut *data);
            let _ = io::copy(&mut cancellable, &mut io::sink());
            if is_cancelled() {
                fatal = Some(ArchiveError::Cancelled);
                return Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()));
            }
            return Ok(true);
        }
        match extract_entry(entry, &mut *data, &dest_root, &mut state) {
            Ok(()) => Ok(true),
            Err(e) if e.is_fatal() => {
                fatal = Some(e);
                Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()))
            }
            Err(e) => {
                warnings.push(format!("{name}: {e}"));
                let mut cancellable = CancelReader::new(&mut *data);
                let _ = io::copy(&mut cancellable, &mut io::sink());
                if is_cancelled() {
                    fatal = Some(ArchiveError::Cancelled);
                    return Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()));
                }
                Ok(true)
            }
        }
    });

    if let Some(e) = fatal {
        return Err(e);
    }
    result.map_err(|e| map_open_err_pw(e, have_password))?;

    for w in &warnings {
        log_warn(format!("7z entry skipped: {w}"));
    }
    Ok(())
}

/// List entry names inside a 7z archive.
pub fn list(archive: &Path) -> Result<Vec<String>> {
    let reader = ArchiveReader::open(archive, Password::empty()).map_err(map_open_err)?;
    Ok(reader
        .archive()
        .files
        .iter()
        .map(|f| f.name().to_string())
        .collect())
}

pub fn list_detailed(archive: &Path) -> Result<PreviewListing> {
    list_detailed_impl(archive, None)
}

pub fn list_detailed_with_password(archive: &Path, password: &str) -> Result<PreviewListing> {
    if password.is_empty() {
        return list_detailed_impl(archive, None);
    }
    list_detailed_impl(archive, Some(password))
}

fn entry_is_encrypted(archive: &sevenz_rust2::Archive, index: usize) -> bool {
    archive
        .stream_map
        .file_block_index
        .get(index)
        .and_then(|block| *block)
        .and_then(|block| archive.blocks.get(block))
        .is_some_and(|block| {
            block
                .coders
                .iter()
                .any(|c| c.encoder_method_id() == sevenz_rust2::EncoderMethod::ID_AES256_SHA256)
        })
}

fn list_detailed_impl(archive: &Path, password: Option<&str>) -> Result<PreviewListing> {
    let (pw, have_password) = match password {
        Some(p) => password_of(p),
        None => (Password::empty(), false),
    };
    let reader = ArchiveReader::open(archive, pw).map_err(|e| map_open_err_pw(e, have_password))?;
    let parsed = reader.archive();
    let mut out = Vec::with_capacity(parsed.files.len());
    for (index, f) in parsed.files.iter().enumerate() {
        check_cancelled()?;
        let is_dir = f.is_directory();
        let modified = if f.has_last_modified_date {
            let st: std::time::SystemTime = f.last_modified_date().into();
            st.duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_millis() as u64)
                .unwrap_or(0)
        } else {
            0
        };
        out.push(PreviewEntry {
            name: f.name().to_string(),
            size: if is_dir { 0 } else { f.size() },
            is_dir,
            encrypted: entry_is_encrypted(parsed, index),
            modified,
            mode: 0,
        });
    }
    Ok(PreviewListing::new(out))
}

pub fn search_content(
    archive: &Path,
    needle: &str,
    case_sensitive: bool,
    password: Option<&str>,
    max_bytes: u64,
) -> Result<Vec<ContentMatch>> {
    let (pw, have_password) = match password {
        Some(p) => password_of(p),
        None => (Password::empty(), false),
    };
    let mut reader =
        ArchiveReader::open(archive, pw).map_err(|e| map_open_err_pw(e, have_password))?;
    let mut out: Vec<ContentMatch> = Vec::new();
    let mut fatal: Option<ArchiveError> = None;
    let needle = needle.to_string();
    let result = reader.for_each_entries(|entry, data| {
        if is_cancelled() {
            fatal = Some(ArchiveError::Cancelled);
            return Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()));
        }
        if fatal.is_some() || entry.is_directory() {
            return Ok(true);
        }
        let name = entry.name().to_string();
        let Some(mut scanner) = Scanner::new(&needle, case_sensitive, max_bytes) else {
            return Ok(true);
        };
        let mut buf = vec![0u8; 64 * 1024];
        while !scanner.is_done() {
            if is_cancelled() {
                fatal = Some(ArchiveError::Cancelled);
                return Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()));
            }
            match data.read(&mut buf) {
                Ok(0) => break,
                Ok(n) => scanner.feed(&buf[..n]),
                Err(e) => {
                    fatal = Some(ArchiveError::Io(e));
                    return Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()));
                }
            }
        }
        if let Some(m) = scanner.finish() {
            out.push(ContentMatch {
                name,
                line: m.line,
                snippet: m.snippet,
            });
        }
        Ok(true)
    });
    if let Some(e) = fatal {
        return Err(e);
    }
    result.map_err(|e| map_open_err_pw(e, have_password))?;
    Ok(out)
}

pub fn test(archive: &Path, limits: &Limits) -> Result<TestReport> {
    test_impl(archive, limits, None)
}

pub fn test_with_password(archive: &Path, limits: &Limits, password: &str) -> Result<TestReport> {
    if password.is_empty() {
        return test_impl(archive, limits, None);
    }
    test_impl(archive, limits, Some(password))
}

fn test_impl(archive: &Path, limits: &Limits, password: Option<&str>) -> Result<TestReport> {
    progress_reset(0);
    let (pw, have_password) = match password {
        Some(p) => password_of(p),
        None => (Password::empty(), false),
    };
    let mut reader =
        ArchiveReader::open(archive, pw).map_err(|e| map_open_err_pw(e, have_password))?;
    let mut state = LimitState::new(limits);
    let mut failures: Vec<TestFailure> = Vec::new();
    let mut total_size: u64 = 0;
    let mut count: usize = 0;
    let mut fatal: Option<ArchiveError> = None;

    let result = reader.for_each_entries(|entry, data| {
        if is_cancelled() {
            fatal = Some(ArchiveError::Cancelled);
            return Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()));
        }
        if fatal.is_some() {
            return Ok(true);
        }
        if check_cancelled().is_err() {
            fatal = Some(ArchiveError::Cancelled);
            return Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()));
        }
        let name = entry.name().to_string();
        count += 1;
        if entry.is_directory() {
            match state.begin_entry(&name, Some(0)) {
                Ok(()) => {}
                Err(e) if e.is_fatal() => {
                    fatal = Some(e);
                    return Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()));
                }
                Err(e) => {
                    failures.push(TestFailure {
                        name,
                        reason: e.to_string(),
                    });
                }
            }
            return Ok(true);
        }
        if crate::io_util::sanitize_entry_name(&name).is_err() {
            failures.push(TestFailure {
                name,
                reason: "unsafe entry name".to_string(),
            });
            let mut cancellable = CancelReader::new(&mut *data);
            let _ = io::copy(&mut cancellable, &mut io::sink());
            return Ok(true);
        }
        let declared = entry.size();
        match state.begin_entry(&name, Some(declared)) {
            Ok(()) => {}
            Err(e) if e.is_fatal() => {
                fatal = Some(e);
                return Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()));
            }
            Err(e) => {
                failures.push(TestFailure {
                    name,
                    reason: e.to_string(),
                });
                let mut cancellable = CancelReader::new(&mut *data);
                let _ = io::copy(&mut cancellable, &mut io::sink());
                return Ok(true);
            }
        }
        let allowance = state.allowance(Some(declared));
        let mut limited = LimitedReader::new(&mut *data, allowance);
        let mut sink = io::sink();
        match io::copy(&mut limited, &mut sink).map_err(classify_io) {
            Ok(_) => {
                let written = limited.count();
                match state.finish_entry(&name, Some(declared), written) {
                    Ok(()) => {
                        total_size = total_size.saturating_add(written);
                    }
                    Err(e) if e.is_fatal() => {
                        fatal = Some(e);
                        return Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()));
                    }
                    Err(e) => {
                        failures.push(TestFailure {
                            name,
                            reason: e.to_string(),
                        });
                    }
                }
                Ok(true)
            }
            Err(e) if e.is_fatal() => {
                fatal = Some(e);
                Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()))
            }
            Err(e) => {
                failures.push(TestFailure {
                    name,
                    reason: e.to_string(),
                });
                let mut cancellable = CancelReader::new(&mut *data);
                let _ = io::copy(&mut cancellable, &mut io::sink());
                if is_cancelled() {
                    fatal = Some(ArchiveError::Cancelled);
                    return Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()));
                }
                Ok(true)
            }
        }
    });

    if let Some(e) = fatal {
        return Err(e);
    }
    result.map_err(|e| map_open_err_pw(e, have_password))?;

    Ok(TestReport {
        entries: count,
        total_size,
        failures,
        password_required: false,
    })
}

fn trim_name(value: &str) -> String {
    value.trim_end_matches('/').to_string()
}

fn entry_matches(entry: &str, target: &str) -> bool {
    let e = entry.trim_end_matches('/');
    let t = target.trim_end_matches('/');
    if t.is_empty() {
        return false;
    }
    e == t || e.starts_with(&format!("{t}/"))
}

fn normalize_targets(names: &[String]) -> Result<Vec<String>> {
    let mut out = Vec::with_capacity(names.len());
    for n in names {
        let s = sanitize_entry_name(n)?;
        if s.is_empty() {
            return Err(ArchiveError::invalid("empty entry name"));
        }
        out.push(trim_name(&s));
    }
    Ok(out)
}

fn normalize_dest_dir(dest_dir: &str) -> Result<String> {
    let t = dest_dir.trim().replace('\\', "/");
    let t = t.trim_matches('/').to_string();
    if t.is_empty() {
        return Ok(String::new());
    }
    Ok(trim_name(&sanitize_entry_name(&t)?))
}

enum EditKind {
    Delete(Vec<String>),
    Rename {
        from: String,
        to: String,
    },
    Add {
        replace: HashSet<String>,
    },
    Meta {
        target: String,
        modified_millis: u64,
    },
}

fn validate_rename(existing: &[String], from: &str, to: &str) -> Result<()> {
    let mut from_found = false;
    for n in existing {
        if entry_matches(n, from) {
            from_found = true;
            break;
        }
    }
    if !from_found {
        return Err(ArchiveError::invalid(format!("entry '{from}' not found")));
    }
    let outside: HashSet<String> = existing
        .iter()
        .filter(|n| !entry_matches(n, from))
        .cloned()
        .collect();
    for n in existing {
        if entry_matches(n, from) {
            let rest = n[from.len()..].to_string();
            let candidate = format!("{to}{rest}");
            if outside.contains(&candidate) {
                return Err(ArchiveError::invalid(format!(
                    "entry '{candidate}' already exists"
                )));
            }
        }
    }
    if outside.contains(to) {
        return Err(ArchiveError::invalid(format!(
            "entry '{to}' already exists"
        )));
    }
    Ok(())
}

fn drain_entry(data: &mut dyn Read) -> Result<()> {
    let mut cancellable = CancelReader::new(data);
    io::copy(&mut cancellable, &mut io::sink()).map_err(classify_io)?;
    Ok(())
}

fn edit_impl(
    archive: &Path,
    password: Option<&str>,
    kind: EditKind,
    additions: &[SourceEntry],
) -> Result<()> {
    let (pw, have_password) = match password {
        Some(p) => password_of(p),
        None => (Password::empty(), false),
    };
    let mut reader =
        ArchiveReader::open(archive, pw).map_err(|e| map_open_err_pw(e, have_password))?;

    let existing: Vec<String> = reader
        .archive()
        .files
        .iter()
        .map(|f| trim_name(f.name()))
        .collect();
    if let EditKind::Rename { from, to } = &kind {
        validate_rename(&existing, from, to)?;
    }
    if let EditKind::Meta { target, .. } = &kind
        && !existing.iter().any(|n| n == target)
    {
        return Err(ArchiveError::invalid(format!("entry '{target}' not found")));
    }

    let limits = Limits::default();
    let af = AtomicFile::new(archive)?;
    let mut state = LimitState::new(&limits);
    let mut writer = ArchiveWriter::create(af.path()).map_err(map_err)?;
    if let Some(pw) = password.filter(|p| !p.is_empty()) {
        writer.set_content_methods(vec![
            AesEncoderOptions::new(Password::new(pw)).into(),
            lzma2_options().into(),
        ]);
    } else {
        writer.set_content_methods(vec![lzma2_options().into()]);
    }
    progress_reset(existing.len() as u64 + additions.len() as u64);

    let mut batch_entries: Vec<ArchiveEntry> = Vec::new();
    let mut batch_readers: Vec<SourceReader<Box<dyn Read>>> = Vec::new();
    let mut batch_meta: Vec<(String, u64)> = Vec::new();
    let mut batch_bytes: u64 = 0;
    let mut fatal: Option<ArchiveError> = None;

    let mut step = |entry: &ArchiveEntry, data: &mut dyn Read| -> Result<()> {
        let name = entry.name().to_string();
        let trimmed = trim_name(&name);

        match &kind {
            EditKind::Delete(targets) => {
                if targets.iter().any(|t| entry_matches(&name, t)) {
                    drain_entry(data)?;
                    progress_add(1);
                    return Ok(());
                }
            }
            EditKind::Add { replace } => {
                if replace.contains(&trimmed) {
                    drain_entry(data)?;
                    progress_add(1);
                    return Ok(());
                }
            }
            EditKind::Rename { .. } | EditKind::Meta { .. } => {}
        }

        let mut out = entry.clone();
        match &kind {
            EditKind::Rename { from, to } => {
                if entry_matches(&name, from) {
                    let rest = trimmed[from.len()..].to_string();
                    out.name = format!("{to}{rest}");
                }
            }
            EditKind::Meta {
                target,
                modified_millis,
            } => {
                if trimmed == *target {
                    let st = std::time::SystemTime::UNIX_EPOCH
                        + std::time::Duration::from_millis(*modified_millis);
                    let nt = NtTime::try_from(st)
                        .map_err(|_| ArchiveError::invalid("modified time out of range"))?;
                    out.last_modified_date = nt;
                    out.has_last_modified_date = true;
                }
            }
            EditKind::Delete(_) | EditKind::Add { .. } => {}
        }

        let final_name = out.name.clone();
        if out.is_directory || !out.has_stream {
            flush_solid_batch(
                &mut writer,
                &mut batch_entries,
                &mut batch_readers,
                &mut batch_meta,
                &mut state,
            )?;
            batch_bytes = 0;
            writer
                .push_archive_entry::<io::Empty>(out, None)
                .map_err(map_err)?;
            progress_add(1);
            return Ok(());
        }

        let declared = entry.size();
        state.begin_entry(&final_name, Some(declared))?;
        if declared >= SOLID_MAX_BYTES {
            flush_solid_batch(
                &mut writer,
                &mut batch_entries,
                &mut batch_readers,
                &mut batch_meta,
                &mut state,
            )?;
            batch_bytes = 0;
            let allowance = state.allowance(Some(declared));
            let mut limited = LimitedReader::new(data, allowance);
            writer
                .push_archive_entry(out, Some(&mut limited))
                .map_err(map_err)?;
            state.finish_entry(&final_name, Some(declared), declared)?;
        } else {
            if batch_entries.len() >= SOLID_MAX_FILES
                || batch_bytes.saturating_add(declared) > SOLID_MAX_BYTES
            {
                flush_solid_batch(
                    &mut writer,
                    &mut batch_entries,
                    &mut batch_readers,
                    &mut batch_meta,
                    &mut state,
                )?;
                batch_bytes = 0;
            }
            let allowance = state.allowance(Some(declared));
            let mut limited = LimitedReader::new(data, allowance);
            let mut buf = Vec::with_capacity(declared as usize);
            io::copy(&mut limited, &mut buf).map_err(classify_io)?;
            let reader: Box<dyn Read> = Box::new(io::Cursor::new(buf));
            batch_bytes = batch_bytes.saturating_add(declared);
            batch_entries.push(out);
            batch_readers.push(SourceReader::new(reader));
            batch_meta.push((final_name, declared));
        }
        progress_add(1);
        Ok(())
    };

    let result = reader.for_each_entries(|entry, data| {
        if fatal.is_some() {
            return Ok(true);
        }
        if is_cancelled() {
            fatal = Some(ArchiveError::Cancelled);
            return Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()));
        }
        match step(entry, data) {
            Ok(()) => Ok(true),
            Err(e) => {
                fatal = Some(e);
                Err(sevenz_rust2::Error::Unsupported("karchiver fatal".into()))
            }
        }
    });
    if let Some(e) = fatal {
        return Err(e);
    }
    result.map_err(|e| map_open_err_pw(e, have_password))?;

    for e in additions {
        check_cancelled()?;
        if e.is_dir {
            flush_solid_batch(
                &mut writer,
                &mut batch_entries,
                &mut batch_readers,
                &mut batch_meta,
                &mut state,
            )?;
            batch_bytes = 0;
            let entry = ArchiveEntry::new_directory(&e.name);
            writer
                .push_archive_entry::<io::Empty>(entry, None)
                .map_err(map_err)?;
            progress_add(1);
            continue;
        }
        state.begin_entry(&e.name, Some(e.size))?;
        let entry = ArchiveEntry::from_path(&e.path, e.name.clone());
        let allowance = state.allowance(Some(e.size));
        let reader: Box<dyn Read> = if e.size == 0 {
            Box::new(io::empty())
        } else {
            Box::new(LimitedReader::new(
                BufReader::with_capacity(READ_BUF_CAP, File::open(&e.path)?),
                allowance,
            ))
        };
        batch_bytes = batch_bytes.saturating_add(e.size);
        batch_entries.push(entry);
        batch_readers.push(SourceReader::new(reader));
        batch_meta.push((e.name.clone(), e.size));
        if batch_entries.len() >= SOLID_MAX_FILES || batch_bytes >= SOLID_MAX_BYTES {
            flush_solid_batch(
                &mut writer,
                &mut batch_entries,
                &mut batch_readers,
                &mut batch_meta,
                &mut state,
            )?;
            batch_bytes = 0;
        }
        progress_add(1);
    }
    flush_solid_batch(
        &mut writer,
        &mut batch_entries,
        &mut batch_readers,
        &mut batch_meta,
        &mut state,
    )?;

    writer.finish()?;
    af.commit()
}

pub fn delete_entries(archive: &Path, names: &[String]) -> Result<()> {
    delete_impl(archive, names, None)
}

pub fn delete_entries_with_password(
    archive: &Path,
    names: &[String],
    password: &str,
) -> Result<()> {
    if password.is_empty() {
        return delete_entries(archive, names);
    }
    delete_impl(archive, names, Some(password))
}

fn delete_impl(archive: &Path, names: &[String], password: Option<&str>) -> Result<()> {
    let targets = normalize_targets(names)?;
    if targets.is_empty() {
        return Ok(());
    }
    edit_impl(archive, password, EditKind::Delete(targets), &[])
}

pub fn rename_entry(archive: &Path, from: &str, to: &str) -> Result<()> {
    rename_impl(archive, from, to, None)
}

pub fn rename_entry_with_password(
    archive: &Path,
    from: &str,
    to: &str,
    password: &str,
) -> Result<()> {
    if password.is_empty() {
        return rename_entry(archive, from, to);
    }
    rename_impl(archive, from, to, Some(password))
}

fn rename_impl(archive: &Path, from: &str, to: &str, password: Option<&str>) -> Result<()> {
    let from_s = trim_name(&sanitize_entry_name(from)?);
    let to_s = trim_name(&sanitize_entry_name(to)?);
    if from_s.is_empty() || to_s.is_empty() {
        return Err(ArchiveError::invalid("empty entry name"));
    }
    if from_s == to_s {
        return Ok(());
    }
    if to_s.starts_with(&format!("{from_s}/")) {
        return Err(ArchiveError::invalid(format!(
            "cannot rename '{from_s}' onto '{to_s}'"
        )));
    }
    edit_impl(
        archive,
        password,
        EditKind::Rename {
            from: from_s,
            to: to_s,
        },
        &[],
    )
}

pub fn add_files(archive: &Path, sources: &[PathBuf], dest_dir: &str) -> Result<()> {
    add_impl(archive, sources, dest_dir, None)
}

pub fn add_files_with_password(
    archive: &Path,
    sources: &[PathBuf],
    dest_dir: &str,
    password: &str,
) -> Result<()> {
    if password.is_empty() {
        return add_files(archive, sources, dest_dir);
    }
    add_impl(archive, sources, dest_dir, Some(password))
}

fn add_impl(
    archive: &Path,
    sources: &[PathBuf],
    dest_dir: &str,
    password: Option<&str>,
) -> Result<()> {
    let prefix = normalize_dest_dir(dest_dir)?;
    let mut additions = collect_sources(sources, &[archive], &Limits::default())?;
    if !prefix.is_empty() {
        for e in &mut additions {
            e.name = format!("{prefix}/{}", e.name);
        }
    }
    if additions.is_empty() {
        return Err(ArchiveError::invalid("no files to add"));
    }
    let replace: HashSet<String> = additions.iter().map(|e| trim_name(&e.name)).collect();
    edit_impl(archive, password, EditKind::Add { replace }, &additions)
}

pub fn set_entry_meta(
    archive: &Path,
    name: &str,
    modified_millis: Option<u64>,
    mode: Option<u32>,
    password: Option<&str>,
) -> Result<()> {
    if mode.is_some() {
        return Err(ArchiveError::Unsupported(
            "7z archives do not support changing entry metadata".to_string(),
        ));
    }
    let Some(ms) = modified_millis else {
        return Ok(());
    };
    let target = trim_name(&sanitize_entry_name(name)?);
    if target.is_empty() {
        return Err(ArchiveError::invalid("empty entry name"));
    }
    edit_impl(
        archive,
        password,
        EditKind::Meta {
            target,
            modified_millis: ms,
        },
        &[],
    )
}

#[cfg(test)]
mod edit_tests {
    use super::*;
    use crate::backend::list_detailed;
    use crate::format::Format;
    use tempfile::tempdir;

    const PASSWORD: &str = "correct-horse-7";

    fn make_7z(dir: &Path) -> PathBuf {
        let src = dir.join("src");
        std::fs::create_dir_all(src.join("mydir/sub")).unwrap();
        std::fs::write(src.join("a.txt"), b"aaa").unwrap();
        std::fs::write(src.join("mydir/b.txt"), b"bbb").unwrap();
        std::fs::write(src.join("mydir/sub/c.txt"), b"ccc").unwrap();
        let dest = dir.join("t.7z");
        crate::backend::compress(
            std::slice::from_ref(&src),
            &dest,
            Format::SevenZ,
            &Limits::default(),
        )
        .unwrap();
        dest
    }

    fn names_of(archive: &Path) -> Vec<String> {
        let listing = list_detailed(archive, Format::SevenZ).unwrap();
        let mut v: Vec<String> = listing.entries.iter().map(|e| e.name.clone()).collect();
        v.sort();
        v
    }

    fn has_entry(archive: &Path, suffix: &str) -> bool {
        names_of(archive).iter().any(|n| n.ends_with(suffix))
    }

    fn extract_to(archive: &Path, dir: &Path) -> PathBuf {
        let out = dir.join("out");
        crate::backend::extract(archive, &out, Format::SevenZ, &Limits::default()).unwrap();
        out
    }

    #[test]
    fn delete_file_keeps_others() {
        let dir = tempdir().unwrap();
        let archive = make_7z(dir.path());
        crate::backend::delete_entries(&archive, Format::SevenZ, &["src/a.txt".to_string()])
            .unwrap();
        assert!(!has_entry(&archive, "a.txt"));
        assert!(has_entry(&archive, "b.txt"));
        let out = extract_to(&archive, dir.path());
        assert!(!out.join("src/a.txt").exists());
        assert_eq!(
            std::fs::read_to_string(out.join("src/mydir/b.txt")).unwrap(),
            "bbb"
        );
        assert_eq!(
            std::fs::read_to_string(out.join("src/mydir/sub/c.txt")).unwrap(),
            "ccc"
        );
    }

    #[test]
    fn delete_dir_subtree() {
        let dir = tempdir().unwrap();
        let archive = make_7z(dir.path());
        crate::backend::delete_entries(&archive, Format::SevenZ, &["src/mydir".to_string()])
            .unwrap();
        let names = names_of(&archive);
        assert!(names.iter().all(|n| !n.contains("mydir")));
        assert!(has_entry(&archive, "a.txt"));
        let out = extract_to(&archive, dir.path());
        assert_eq!(
            std::fs::read_to_string(out.join("src/a.txt")).unwrap(),
            "aaa"
        );
        assert!(!out.join("src/mydir").exists());
    }

    #[test]
    fn rename_file() {
        let dir = tempdir().unwrap();
        let archive = make_7z(dir.path());
        crate::backend::rename_entry(&archive, Format::SevenZ, "src/a.txt", "src/z.txt").unwrap();
        assert!(!has_entry(&archive, "a.txt"));
        assert!(has_entry(&archive, "z.txt"));
        let out = extract_to(&archive, dir.path());
        assert_eq!(
            std::fs::read_to_string(out.join("src/z.txt")).unwrap(),
            "aaa"
        );
        assert_eq!(
            std::fs::read_to_string(out.join("src/mydir/b.txt")).unwrap(),
            "bbb"
        );
    }

    #[test]
    fn rename_dir_prefix() {
        let dir = tempdir().unwrap();
        let archive = make_7z(dir.path());
        crate::backend::rename_entry(&archive, Format::SevenZ, "src/mydir", "src/renamed").unwrap();
        assert!(has_entry(&archive, "renamed/b.txt"));
        assert!(has_entry(&archive, "renamed/sub/c.txt"));
        assert!(!names_of(&archive).iter().any(|n| n.contains("mydir")));
        let out = extract_to(&archive, dir.path());
        assert_eq!(
            std::fs::read_to_string(out.join("src/renamed/b.txt")).unwrap(),
            "bbb"
        );
        assert_eq!(
            std::fs::read_to_string(out.join("src/renamed/sub/c.txt")).unwrap(),
            "ccc"
        );
        assert_eq!(
            std::fs::read_to_string(out.join("src/a.txt")).unwrap(),
            "aaa"
        );
    }

    #[test]
    fn rename_collision_errors() {
        let dir = tempdir().unwrap();
        let archive = make_7z(dir.path());
        let r =
            crate::backend::rename_entry(&archive, Format::SevenZ, "src/a.txt", "src/mydir/b.txt");
        assert!(r.is_err());
        assert!(has_entry(&archive, "a.txt"));
        assert!(has_entry(&archive, "mydir/b.txt"));
    }

    #[test]
    fn rename_missing_entry_errors() {
        let dir = tempdir().unwrap();
        let archive = make_7z(dir.path());
        let r = crate::backend::rename_entry(&archive, Format::SevenZ, "src/nope.txt", "src/z.txt");
        assert!(matches!(r, Err(ArchiveError::Invalid(_))));
    }

    #[test]
    fn add_files_nested() {
        let dir = tempdir().unwrap();
        let archive = make_7z(dir.path());
        let extra = dir.path().join("extra");
        std::fs::create_dir_all(extra.join("nest")).unwrap();
        std::fs::write(extra.join("new.txt"), b"new").unwrap();
        std::fs::write(extra.join("nest/deep.txt"), b"deep").unwrap();
        crate::backend::add_files(&archive, Format::SevenZ, &[extra], "added").unwrap();
        assert!(has_entry(&archive, "added/extra/new.txt"));
        assert!(has_entry(&archive, "added/extra/nest/deep.txt"));
        assert!(has_entry(&archive, "a.txt"));
        let out = extract_to(&archive, dir.path());
        assert_eq!(
            std::fs::read_to_string(out.join("added/extra/new.txt")).unwrap(),
            "new"
        );
        assert_eq!(
            std::fs::read_to_string(out.join("src/a.txt")).unwrap(),
            "aaa"
        );
        assert_eq!(
            std::fs::read_to_string(out.join("src/mydir/sub/c.txt")).unwrap(),
            "ccc"
        );
    }

    #[test]
    fn add_single_file() {
        let dir = tempdir().unwrap();
        let archive = make_7z(dir.path());
        let extra = dir.path().join("extra.txt");
        std::fs::write(&extra, b"new").unwrap();
        crate::backend::add_files(&archive, Format::SevenZ, &[extra], "added").unwrap();
        assert!(has_entry(&archive, "added/extra.txt"));
        assert!(has_entry(&archive, "a.txt"));
        let out = extract_to(&archive, dir.path());
        assert_eq!(
            std::fs::read_to_string(out.join("added/extra.txt")).unwrap(),
            "new"
        );
        assert_eq!(
            std::fs::read_to_string(out.join("src/a.txt")).unwrap(),
            "aaa"
        );
    }

    #[test]
    fn password_delete_roundtrip() {
        let dir = tempdir().unwrap();
        let src = dir.path().join("src");
        std::fs::create_dir_all(&src).unwrap();
        std::fs::write(src.join("a.txt"), b"secret").unwrap();
        std::fs::write(src.join("keep.txt"), b"keep").unwrap();
        let dest = dir.path().join("p.7z");
        crate::backend::compress_with_password(
            std::slice::from_ref(&src),
            &dest,
            Format::SevenZ,
            &Limits::default(),
            PASSWORD,
        )
        .unwrap();
        crate::backend::delete_entries_with_password(
            &dest,
            Format::SevenZ,
            &["src/a.txt".to_string()],
            PASSWORD.as_bytes(),
        )
        .unwrap();

        match crate::backend::list_detailed(&dest, Format::SevenZ) {
            Err(ArchiveError::PasswordRequired(_)) => {}
            other => panic!("expected PasswordRequired, got {other:?}"),
        }
        let out = dir.path().join("out");
        match crate::backend::extract(&dest, &out, Format::SevenZ, &Limits::default()) {
            Err(ArchiveError::PasswordRequired(_)) => {}
            other => panic!("expected PasswordRequired, got {other:?}"),
        }

        crate::backend::extract_with_password(
            &dest,
            &out,
            Format::SevenZ,
            &Limits::default(),
            PASSWORD,
        )
        .unwrap();
        assert!(!out.join("src/a.txt").exists());
        assert_eq!(
            std::fs::read_to_string(out.join("src/keep.txt")).unwrap(),
            "keep"
        );
    }

    #[test]
    fn password_rename_roundtrip() {
        let dir = tempdir().unwrap();
        let src = dir.path().join("src");
        std::fs::create_dir_all(&src).unwrap();
        std::fs::write(src.join("old.txt"), b"old").unwrap();
        let dest = dir.path().join("p.7z");
        crate::backend::compress_with_password(
            std::slice::from_ref(&src),
            &dest,
            Format::SevenZ,
            &Limits::default(),
            PASSWORD,
        )
        .unwrap();
        crate::backend::rename_entry_with_password(
            &dest,
            Format::SevenZ,
            "src/old.txt",
            "src/new.txt",
            PASSWORD.as_bytes(),
        )
        .unwrap();
        let out = dir.path().join("out");
        crate::backend::extract_with_password(
            &dest,
            &out,
            Format::SevenZ,
            &Limits::default(),
            PASSWORD,
        )
        .unwrap();
        assert!(!out.join("src/old.txt").exists());
        assert_eq!(
            std::fs::read_to_string(out.join("src/new.txt")).unwrap(),
            "old"
        );
    }

    #[test]
    fn password_add_roundtrip() {
        let dir = tempdir().unwrap();
        let src = dir.path().join("src");
        std::fs::create_dir_all(&src).unwrap();
        std::fs::write(src.join("a.txt"), b"aaa").unwrap();
        let dest = dir.path().join("p.7z");
        crate::backend::compress_with_password(
            std::slice::from_ref(&src),
            &dest,
            Format::SevenZ,
            &Limits::default(),
            PASSWORD,
        )
        .unwrap();
        let extra = dir.path().join("extra.txt");
        std::fs::write(&extra, b"new").unwrap();
        crate::backend::add_files_with_password(
            &dest,
            Format::SevenZ,
            &[extra],
            "added",
            PASSWORD.as_bytes(),
        )
        .unwrap();
        let out = dir.path().join("out");
        crate::backend::extract_with_password(
            &dest,
            &out,
            Format::SevenZ,
            &Limits::default(),
            PASSWORD,
        )
        .unwrap();
        assert_eq!(
            std::fs::read_to_string(out.join("added/extra.txt")).unwrap(),
            "new"
        );
        assert_eq!(
            std::fs::read_to_string(out.join("src/a.txt")).unwrap(),
            "aaa"
        );
    }

    #[test]
    fn plain_edit_of_encrypted_requires_password() {
        let dir = tempdir().unwrap();
        let src = dir.path().join("src");
        std::fs::create_dir_all(&src).unwrap();
        std::fs::write(src.join("a.txt"), b"secret").unwrap();
        let dest = dir.path().join("p.7z");
        crate::backend::compress_with_password(
            std::slice::from_ref(&src),
            &dest,
            Format::SevenZ,
            &Limits::default(),
            PASSWORD,
        )
        .unwrap();
        match crate::backend::delete_entries(&dest, Format::SevenZ, &["src/a.txt".to_string()]) {
            Err(ArchiveError::PasswordRequired(_)) => {}
            other => panic!("expected PasswordRequired, got {other:?}"),
        }
        let listing =
            crate::backend::list_detailed_with_password(&dest, Format::SevenZ, PASSWORD).unwrap();
        assert!(listing.entries.iter().any(|e| e.name.ends_with("a.txt")));
    }

    #[test]
    fn set_entry_meta_updates_mtime() {
        let dir = tempdir().unwrap();
        let archive = make_7z(dir.path());
        let before = list_detailed(&archive, Format::SevenZ).unwrap();
        let other_modified = before
            .entries
            .iter()
            .find(|e| e.name.ends_with("b.txt"))
            .unwrap()
            .modified;

        let target_ms = 1_600_000_000_000u64;
        crate::backend::set_entry_meta(
            &archive,
            Format::SevenZ,
            "src/a.txt",
            Some(target_ms),
            None,
            None,
        )
        .unwrap();

        let after = list_detailed(&archive, Format::SevenZ).unwrap();
        let a = after
            .entries
            .iter()
            .find(|e| e.name.ends_with("a.txt"))
            .unwrap();
        assert_eq!(a.modified, target_ms);
        let b = after
            .entries
            .iter()
            .find(|e| e.name.ends_with("b.txt"))
            .unwrap();
        assert_eq!(b.modified, other_modified);

        let out = extract_to(&archive, dir.path());
        assert_eq!(
            std::fs::read_to_string(out.join("src/a.txt")).unwrap(),
            "aaa"
        );
        assert_eq!(
            std::fs::read_to_string(out.join("src/mydir/b.txt")).unwrap(),
            "bbb"
        );
    }

    #[test]
    fn set_entry_meta_mode_stays_unsupported() {
        let dir = tempdir().unwrap();
        let archive = make_7z(dir.path());
        let r = crate::backend::set_entry_meta(
            &archive,
            Format::SevenZ,
            "src/a.txt",
            None,
            Some(0o600),
            None,
        );
        assert!(matches!(r, Err(ArchiveError::Unsupported(_))));
        let r2 = crate::backend::set_entry_meta(
            &archive,
            Format::SevenZ,
            "src/a.txt",
            Some(1_600_000_000_000),
            Some(0o600),
            None,
        );
        assert!(matches!(r2, Err(ArchiveError::Unsupported(_))));
        assert!(has_entry(&archive, "a.txt"));
    }

    #[test]
    fn set_entry_meta_missing_entry_errors() {
        let dir = tempdir().unwrap();
        let archive = make_7z(dir.path());
        let r = crate::backend::set_entry_meta(
            &archive,
            Format::SevenZ,
            "src/nope.txt",
            Some(1_600_000_000_000),
            None,
            None,
        );
        assert!(matches!(r, Err(ArchiveError::Invalid(_))));
    }
}
