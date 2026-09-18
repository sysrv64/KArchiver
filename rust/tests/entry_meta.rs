use std::path::Path;

use karchiver_rs::backend;
use karchiver_rs::error::ArchiveError;
use karchiver_rs::format::Format;
use karchiver_rs::io_util::Limits;
use tempfile::tempdir;

fn make_tree(root: &Path) {
    std::fs::create_dir_all(root).unwrap();
    std::fs::write(root.join("a.txt"), b"aaa").unwrap();
    std::fs::write(root.join("b.txt"), b"bbb").unwrap();
}

fn find<'a>(listing: &'a backend::PreviewListing, suffix: &str) -> &'a backend::PreviewEntry {
    listing
        .entries
        .iter()
        .find(|e| e.name.ends_with(suffix))
        .expect("entry present")
}

#[test]
fn zip_set_entry_meta_updates_only_target() {
    let dir = tempdir().unwrap();
    let src = dir.path().join("src");
    make_tree(&src);
    let archive = dir.path().join("t.zip");
    backend::compress(
        std::slice::from_ref(&src),
        &archive,
        Format::Zip,
        &Limits::default(),
    )
    .unwrap();

    let before = backend::list_detailed(&archive, Format::Zip).unwrap();
    let other_mode = find(&before, "b.txt").mode;
    let other_modified = find(&before, "b.txt").modified;
    let target_modified_before = find(&before, "a.txt").modified;

    let target_ms = 1_600_000_000_000u64;
    backend::set_entry_meta(
        &archive,
        Format::Zip,
        "src/a.txt",
        Some(target_ms),
        Some(0o600),
        None,
    )
    .unwrap();

    let after = backend::list_detailed(&archive, Format::Zip).unwrap();
    assert_ne!(after.entries.len(), 0);
    let a = find(&after, "a.txt");
    assert_eq!(a.mode, 0o600);
    assert_eq!(a.modified, target_ms);
    assert_ne!(a.modified, target_modified_before);
    let b = find(&after, "b.txt");
    assert_eq!(b.mode, other_mode);
    assert_eq!(b.modified, other_modified);

    let out = dir.path().join("out");
    backend::extract(&archive, &out, Format::Zip, &Limits::default()).unwrap();
    assert_eq!(std::fs::read(out.join("src/a.txt")).unwrap(), b"aaa");
    assert_eq!(std::fs::read(out.join("src/b.txt")).unwrap(), b"bbb");
}

#[test]
fn zip_set_entry_meta_fields_are_independent() {
    let dir = tempdir().unwrap();
    let src = dir.path().join("src");
    make_tree(&src);
    let archive = dir.path().join("t.zip");
    backend::compress(
        std::slice::from_ref(&src),
        &archive,
        Format::Zip,
        &Limits::default(),
    )
    .unwrap();

    let before = backend::list_detailed(&archive, Format::Zip).unwrap();
    let before_modified = find(&before, "a.txt").modified;

    let target_ms = 1_600_000_000_000u64;
    backend::set_entry_meta(&archive, Format::Zip, "src/a.txt", None, Some(0o640), None).unwrap();
    let only_mode = backend::list_detailed(&archive, Format::Zip).unwrap();
    let a = find(&only_mode, "a.txt");
    assert_eq!(a.mode, 0o640);
    assert_eq!(a.modified, before_modified);

    backend::set_entry_meta(
        &archive,
        Format::Zip,
        "src/a.txt",
        Some(target_ms),
        None,
        None,
    )
    .unwrap();
    let only_time = backend::list_detailed(&archive, Format::Zip).unwrap();
    let a = find(&only_time, "a.txt");
    assert_eq!(a.mode, 0o640);
    assert_eq!(a.modified, target_ms);
}

#[test]
fn zip_set_entry_meta_rejects_missing_and_escaping_names() {
    let dir = tempdir().unwrap();
    let src = dir.path().join("src");
    make_tree(&src);
    let archive = dir.path().join("t.zip");
    backend::compress(
        std::slice::from_ref(&src),
        &archive,
        Format::Zip,
        &Limits::default(),
    )
    .unwrap();

    let missing = backend::set_entry_meta(
        &archive,
        Format::Zip,
        "src/nope.txt",
        Some(1_600_000_000_000),
        None,
        None,
    );
    assert!(matches!(missing, Err(ArchiveError::Invalid(_))));
    let escaping =
        backend::set_entry_meta(&archive, Format::Zip, "../evil", None, Some(0o600), None);
    assert!(matches!(escaping, Err(ArchiveError::Security(_))));
}

#[test]
fn tar_set_entry_meta_updates_only_target() {
    let dir = tempdir().unwrap();
    let src = dir.path().join("src");
    make_tree(&src);
    let archive = dir.path().join("t.tar");
    backend::compress(
        std::slice::from_ref(&src),
        &archive,
        Format::Tar,
        &Limits::default(),
    )
    .unwrap();

    let before = backend::list_detailed(&archive, Format::Tar).unwrap();
    let other_mode = find(&before, "b.txt").mode;
    let other_modified = find(&before, "b.txt").modified;

    let target_ms = 1_600_000_000_000u64;
    backend::set_entry_meta(
        &archive,
        Format::Tar,
        "src/a.txt",
        Some(target_ms),
        Some(0o600),
        None,
    )
    .unwrap();

    let after = backend::list_detailed(&archive, Format::Tar).unwrap();
    let a = find(&after, "a.txt");
    assert_eq!(a.mode, 0o600);
    assert_eq!(a.modified, target_ms);
    let b = find(&after, "b.txt");
    assert_eq!(b.mode, other_mode);
    assert_eq!(b.modified, other_modified);

    let out = dir.path().join("out");
    backend::extract(&archive, &out, Format::Tar, &Limits::default()).unwrap();
    assert_eq!(std::fs::read(out.join("src/a.txt")).unwrap(), b"aaa");
    assert_eq!(std::fs::read(out.join("src/b.txt")).unwrap(), b"bbb");
}

#[test]
fn tar_gz_set_entry_meta_roundtrips() {
    let dir = tempdir().unwrap();
    let src = dir.path().join("src");
    make_tree(&src);
    let archive = dir.path().join("t.tar.gz");
    backend::compress(
        std::slice::from_ref(&src),
        &archive,
        Format::TarGz,
        &Limits::default(),
    )
    .unwrap();

    let target_ms = 1_600_000_000_000u64;
    backend::set_entry_meta(
        &archive,
        Format::TarGz,
        "src/a.txt",
        Some(target_ms),
        Some(0o600),
        None,
    )
    .unwrap();

    let after = backend::list_detailed(&archive, Format::TarGz).unwrap();
    let a = find(&after, "a.txt");
    assert_eq!(a.mode, 0o600);
    assert_eq!(a.modified, target_ms);
    assert!(after.entries.iter().any(|e| e.name.ends_with("b.txt")));
}

#[test]
fn zip_set_entry_meta_coerces_out_of_range_times() {
    let dir = tempdir().unwrap();
    let src = dir.path().join("src");
    make_tree(&src);
    let archive = dir.path().join("t.zip");
    backend::compress(
        std::slice::from_ref(&src),
        &archive,
        Format::Zip,
        &Limits::default(),
    )
    .unwrap();

    backend::set_entry_meta(&archive, Format::Zip, "src/a.txt", Some(0), None, None).unwrap();
    let low = backend::list_detailed(&archive, Format::Zip).unwrap();
    assert_eq!(find(&low, "a.txt").modified, 315_532_800_000);

    backend::set_entry_meta(
        &archive,
        Format::Zip,
        "src/a.txt",
        Some(9_000_000_000_000),
        None,
        None,
    )
    .unwrap();
    let high = backend::list_detailed(&archive, Format::Zip).unwrap();
    let clamped = find(&high, "a.txt").modified;
    assert!(clamped > 4_000_000_000_000);
    assert!(clamped < 9_000_000_000_000);
}

#[test]
fn unsupported_formats_return_unsupported() {
    let rar = backend::set_entry_meta(Path::new("x.rar"), Format::Rar, "a", Some(1), None, None);
    assert!(matches!(rar, Err(ArchiveError::Unsupported(_))));
    let sevenz = backend::set_entry_meta(
        Path::new("x.7z"),
        Format::SevenZ,
        "a",
        None,
        Some(0o600),
        None,
    );
    assert!(matches!(sevenz, Err(ArchiveError::Unsupported(_))));
    let gz = backend::set_entry_meta(Path::new("x.gz"), Format::Gzip, "a", Some(1), None, None);
    assert!(matches!(gz, Err(ArchiveError::Unsupported(_))));
}

#[test]
fn both_none_is_noop_success() {
    let ok = backend::set_entry_meta(
        Path::new("missing.zip"),
        Format::Zip,
        "nope",
        None,
        None,
        None,
    );
    assert!(ok.is_ok());
    let ok_rar = backend::set_entry_meta(
        Path::new("missing.rar"),
        Format::Rar,
        "nope",
        None,
        None,
        None,
    );
    assert!(ok_rar.is_ok());
}
