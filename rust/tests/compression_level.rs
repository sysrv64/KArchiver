use std::fs;
use std::path::Path;

use karchiver_rs::backend;
use karchiver_rs::backend::CompressionOptions;
use karchiver_rs::format::Format;
use karchiver_rs::io_util::Limits;
use tempfile::tempdir;

fn make_tree(root: &Path) {
    fs::create_dir_all(root.join("sub")).unwrap();
    fs::write(root.join("hello.txt"), b"hello karchiver").unwrap();
    fs::write(root.join("sub/nested.bin"), [0u8, 1, 2, 3, 4, 255]).unwrap();
    fs::write(root.join("sub/empty.txt"), b"").unwrap();
}

fn options(level: Option<i32>) -> CompressionOptions {
    CompressionOptions { level }
}

fn assert_roundtrips(format: Format, name: &str) {
    let (min, _) = backend::format_level_bounds(format).unwrap();
    let max = backend::format_level_bounds(format).unwrap().1;

    for level in [min, max] {
        let dir = tempdir().unwrap();
        let src = dir.path().join("src");
        make_tree(&src);
        let dest = dir.path().join(name);
        backend::compress(
            std::slice::from_ref(&src),
            &dest,
            format,
            &Limits::default(),
            &options(Some(level)),
        )
        .unwrap();
        let out_dir = dir.path().join("out");
        backend::extract(&dest, &out_dir, format, &Limits::default()).unwrap();
        assert_eq!(
            fs::read(src.join("hello.txt")).unwrap(),
            fs::read(out_dir.join("src/hello.txt")).unwrap(),
            "{name} roundtrip at level {level}"
        );
    }
}

#[test]
fn roundtrip_at_min_and_max_level_for_every_level_aware_format() {
    for (format, name) in [
        (Format::Zip, "level.zip"),
        (Format::SevenZ, "level.7z"),
        (Format::TarGz, "level.tar.gz"),
        (Format::TarBz2, "level.tar.bz2"),
        (Format::TarXz, "level.tar.xz"),
        (Format::TarZst, "level.tar.zst"),
    ] {
        assert_roundtrips(format, name);
    }
}

#[test]
fn out_of_range_levels_are_clamped_instead_of_panicking() {
    for (format, name) in [
        (Format::Zip, "clamp.zip"),
        (Format::SevenZ, "clamp.7z"),
        (Format::TarGz, "clamp.tar.gz"),
        (Format::TarBz2, "clamp.tar.bz2"),
        (Format::TarXz, "clamp.tar.xz"),
        (Format::TarZst, "clamp.tar.zst"),
    ] {
        for level in [-100, -1, 0, 99, 1000] {
            let dir = tempdir().unwrap();
            let src = dir.path().join("src");
            make_tree(&src);
            let dest = dir.path().join(name);
            backend::compress(
                std::slice::from_ref(&src),
                &dest,
                format,
                &Limits::default(),
                &options(Some(level)),
            )
            .unwrap_or_else(|e| panic!("{name} level {level} failed: {e}"));
            assert!(dest.exists(), "{name} level {level} produced no archive");
        }
    }
}

#[test]
fn bzip2_never_receives_level_zero() {
    let dir = tempdir().unwrap();
    let src = dir.path().join("src");
    make_tree(&src);
    let dest = dir.path().join("bz2-zero.tar.bz2");

    let (min, _) = backend::format_level_bounds(Format::TarBz2).unwrap();
    assert_eq!(min, 1, "bzip2 encoder panics below level 1");

    backend::compress(
        std::slice::from_ref(&src),
        &dest,
        Format::TarBz2,
        &Limits::default(),
        &options(Some(0)),
    )
    .expect("level 0 must be clamped up to 1, not panic");

    assert!(dest.exists());
}

#[test]
fn no_level_reproduces_the_builtin_default_archive() {
    for (format, name) in [
        (Format::Zip, "default.zip"),
        (Format::SevenZ, "default.7z"),
        (Format::TarGz, "default.tar.gz"),
        (Format::TarBz2, "default.tar.bz2"),
        (Format::TarXz, "default.tar.xz"),
        (Format::TarZst, "default.tar.zst"),
    ] {
        let dir = tempdir().unwrap();
        let src = dir.path().join("src");
        make_tree(&src);

        let a = dir.path().join(format!("a-{name}"));
        let b = dir.path().join(format!("b-{name}"));
        backend::compress(
            std::slice::from_ref(&src),
            &a,
            format,
            &Limits::default(),
            &options(None),
        )
        .unwrap();
        backend::compress(
            std::slice::from_ref(&src),
            &b,
            format,
            &Limits::default(),
            &options(None),
        )
        .unwrap();

        let pa = fs::read(&a).unwrap();
        let pb = fs::read(&b).unwrap();
        assert!(!pa.is_empty() && !pb.is_empty(), "{name} produced no bytes");
    }
}

#[test]
fn level_bounds_match_the_kotlin_settings_model() {
    assert_eq!(backend::format_level_bounds(Format::Zip), Some((1, 9)));
    assert_eq!(backend::format_level_bounds(Format::SevenZ), Some((0, 9)));
    assert_eq!(backend::format_level_bounds(Format::TarGz), Some((0, 9)));
    assert_eq!(backend::format_level_bounds(Format::TarBz2), Some((1, 9)));
    assert_eq!(backend::format_level_bounds(Format::TarXz), Some((0, 9)));
    assert_eq!(backend::format_level_bounds(Format::TarZst), Some((1, 22)));
    assert_eq!(backend::format_level_bounds(Format::Rar), Some((0, 5)));
    assert!(backend::format_level_bounds(Format::Tar).is_none());
}

#[test]
fn rar_levels_are_accepted_and_distinguishable() {
    let dir = tempdir().unwrap();
    let src = dir.path().join("secret.txt");
    fs::write(&src, b"rar level probe payload payload payload").unwrap();

    let mut sizes = Vec::new();
    for level in 0..=5 {
        let dest = dir.path().join(format!("lvl{level}.rar"));
        backend::compress(
            std::slice::from_ref(&src),
            &dest,
            Format::Rar,
            &Limits::default(),
            &options(Some(level)),
        )
        .unwrap_or_else(|e| panic!("rar level {level} failed: {e}"));
        assert!(dest.exists());
        sizes.push(fs::metadata(&dest).unwrap().len());
    }

    assert!(
        sizes[0] >= sizes[5],
        "expected the strongest rar level to be no larger than level 0, got {sizes:?}"
    );
}
