# Repository context

## About the project

- **Name:** KArchiver
- **One-line description:** An Android file manager with a built-in archive
  tool: browse storage, and create, browse, extract and edit archives.
- **Audience:** Android end users who manage files and archives on a phone,
  including power users who use root or Shizuku for restricted paths.
- **Links:** README — https://github.com/sysrv64/KArchiver/blob/main/README.md,
  QA — https://github.com/sysrv64/KArchiver/blob/main/QA.md,
  Wiki — https://github.com/sysrv64/KArchiver/wiki

## Tech stack

- **Languages/versions:** Kotlin (Compose), Rust (core), Gradle/AGP, JDK 17.
- **Frameworks/libraries:** Jetpack Compose with Material 3 Expressive,
  Navigation Compose, DataStore, Room, Coil 3, kotlinx.serialization,
  coroutines; Rust uses `zip`, `sevenz-rust2`, `rars`, `tar`, `jni`.
- **Infrastructure:** GitHub Actions builds debug and release APKs on every
  push to `main`; release APKs are signed with a keystore held in repository
  secrets. The app targets Android 8.0+ (minSdk 26, targetSdk 37).

## Repository structure

| Path | Purpose |
|---|---|
| `app/src/main/java/com/kerneldroid/karchiver/presentation/` | Compose UI: browser, archive explorer, drawer, settings, trash, recents, history. UI bugs go here. |
| `app/src/main/java/com/kerneldroid/karchiver/data/` | Repositories and platform glue: filesystem access, SAF, elevation (root/Shizuku), archive service, trash, settings, logging. Data/IO/permission bugs go here. |
| `app/src/main/java/com/kerneldroid/karchiver/data/elevation/` | Root and Shizuku engines. Bugs about `/data`, `/vendor`, system paths go here. |
| `rust/src/` | The archive core: `jni_bridge.rs` (JNI ABI), `backend/` (zip, 7z, tar, rar, single-stream), `io_util.rs` (path safety, limits), `content_search.rs`. Archive read/write bugs go here. |
| `rust/tests/` | Rust integration tests. |
| `app/src/test/` | JVM unit tests for pure Kotlin logic. |
| `.github/workflows/build.yml` | CI: builds and signs debug and release APKs. |
| `README.md`, `QA.md` | Feature list and user-facing questions/answers. |

## Conventions and code style

- No comments in code (a hard project rule in both Kotlin and Rust).
- All user-facing strings are in English.
- No emojis in code, commits, or UI.
- The app license is GPL-3.0-only; the Rust core is Apache-2.0.
- A bug fix should come with a test when the logic is testable without a device.

## What counts as a valid issue

- The bug reproduces on the latest build from `main` or the latest release.
- The report states the app version, the device and Android version, the
  storage setup (internal / SD card / SAF / All files access / Shizuku / root),
  and clear reproduction steps.
- For crashes, freezes or wrong output, the report includes logcat lines or
  the saved log file (Settings -> About -> Save logs). A bug report without
  logs is normally `needs-info`, not `bug`.
- The claim points at a real behavior of this codebase, not at a fork or a
  different app.

## Known limitations (not bugs)

- Shizuku runs as the `shell` user, so SELinux blocks `/data`, `/data/data`
  and other users' storage; root is required for those. Issues reporting
  "Shizuku can't open /data" are expected behavior.
- Read-only partitions such as `/system` and `/vendor` cannot be written even
  as root without a remount or an overlay.
- RAR creation is locked behind a long-press unlock on the RAR setting row
  because the library is not well tested yet. RAR is read-only until unlocked.
- Thumbnails run as the app process, so files readable only through root or
  Shizuku show a type icon instead of a preview.
- Files opened from system paths are copied to the app cache first, so the
  normal viewer can display them.
- The debug APK is signed with a throwaway keystore per CI run, so a new debug
  build does not install over an older debug build; that is expected.

## What's out of scope

- Support for Android below 8.0 (minSdk 26).
- Windows or iOS builds.
- Features that require sending user data off the device; the app has no
  network access and does not upload anything.

## Issue and PR templates

This repository uses GitHub issue forms:
`.github/ISSUE_TEMPLATE/bug_report.yml` and
`.github/ISSUE_TEMPLATE/feature_request.yml`. The bot must react to the
template shape: verify the required fields are actually filled in, and when
they are missing, label the issue `needs-info` and ask for the specific
missing piece (version, reproduction steps, or logs) instead of guessing.
