# KArchiver — QA

**Was this project built with AI?**
Yes. It was developed in [opencode](https://opencode.ai).

**Where do I get an APK, and why does the debug signature change every build?**
Two places: the [Releases](https://github.com/sysrv64/KArchiver/releases) page (tagged versions with the APKs, their byte sizes and SHA-256) and GitHub → Actions → the latest successful run on `main` → artifacts. `KArchiver-debug` exists for every run; `KArchiver-release` only for pushes to `main`. Debug APKs are signed with a throwaway keystore generated for that run, so a new debug APK cannot be installed over an older debug install: uninstall the previous build first. Release APKs are signed with a stable key and update normally.

**Installation fails with "APK Signature Scheme v2: SHA-256 digest of contents did not verify" or `INSTALL_PARSE_FAILED_NO_CERTIFICATES`.**
The APK file was modified after it was built. Debug APKs are signed with v2 only, and v2 signs the whole file, so repacking, re-compression, "optimizing", an incomplete download or a copy to a failing SD card breaks it. Compare size and SHA-256 with the release entry or the run summary, re-download the artifact, extract it with a plain extractor, keep it in internal storage and install again, or use `adb install -r app-debug.apk`.

**Does the app need the internet or send any data?**
No. The app does not request the `INTERNET` permission. There is no analytics, telemetry or ads, and history stays in a local database inside the app's private storage.

**Why does the app ask for "All files access"?**
To manage files outside the folders it owns. It is optional: if you deny it, the app falls back to SAF and asks you to grant a storage tree through the system picker, then works inside that tree.

**What are Shizuku and root used for?**
Restricted paths a normal app cannot touch, for example `/Android/data` and system directories. Both are optional (Settings → Elevation). Shizuku needs the Shizuku app installed and running; root needs a working `su`.

**How do I enable RAR?**
Settings → Files → **RAR support**. It is read-only at first. To unlock creating RAR archives, press and hold the same row for 5 seconds until a haptic tick confirms; RAR packing is a deliberate speed bump because the library has not been well tested yet.

**Which archives can be edited in place?**
ZIP, TAR, TAR.GZ, TAR.BZ2, TAR.XZ, TAR.ZST and 7Z support adding, renaming and deleting entries. RAR and single-stream archives (`.gz`, `.bz2`, `.xz`, `.zst`, `.lz4`) are read-only.

**Does extraction overwrite my files?**
Not without asking. When names collide you choose Replace, Skip or Keep both; "Keep both" writes a new name such as `report (1).pdf`.

**What can I type into the search field?**
Plain words match names, tokens refine the query, several tokens combine with AND, and invalid values fall back to a normal name match.

| Token | Meaning |
| --- | --- |
| `name:` / `n:` | name contains value |
| `ext:` / `format:` / `f:` | extension, for example `f:zip,pdf` |
| `date:` / `d:` | `today`, `yesterday`, `2026-09-17`, `2026-09`, `2026-09-01..2026-09-30`, `>=2026-09-01` |
| `size:` / `s:` | `>10MB`, `1MB..100MB`, `5M` |
| `type:` / `is:` | `file` or `dir` |
| `content:` / `text:` / `c:` | file or archive entry content contains value |
| `archive:` / `a:` | archive has an entry whose name contains value |

Quote values with spaces (`content:"hello world"`). Content and archive searches are configured in Settings → Search: enable content search, archive search, case sensitivity and the per-file scan limit. Such queries walk the current folder tree, show a "Scanned N files" counter, stop after 500 results or 20,000 scanned files, and are cancelled when the query changes; nothing is indexed in the background.

**What does History store, and how do I turn it off?**
Recent folders and opened files with type filters, kept in a local Room database in the app's private storage. Remove the **History** tab in the drawer to stop recording and add it back to resume; nothing is recorded while it is missing and the database can be cleared from the History screen.

**What does Trash do, and where do my files go?**
It is off by default. Add the **Trash** tab by holding any empty spot in the drawer and picking it from the menu, and the feature turns on; remove that tab and it turns off again. While it is on, deleting files in the browser moves them into a hidden `.karchiver-trash` folder on the same storage volume (or into the app's private storage when the location is outside a known volume) instead of erasing them. In the Trash tab you can restore an item to its original path, delete it forever, or empty the whole Trash. Moving to Trash needs direct access to the location (All files access, or an elevated engine); if the move fails the file is left untouched and an error is shown. Deleting entries inside an archive is always permanent. Trash items are kept on disk when the tab is removed, so adding the tab back brings them along.

**How do I reorder, remove or restore the tabs in the navigation drawer?**
Hold a tab still briefly to open a small menu that removes it; hold the right edge of a tab and drag it to move it. When a tab is missing, hold any empty spot in the drawer (outside the tab rows) to open a menu that adds it back. Pinned favorites can be reordered the same way: drag a favorite from its right edge. Files and Settings cannot be removed. Removing the **Trash** tab switches Trash off and adding it back switches it on; removing **History** stops history recording and adding it back resumes it. The whole drawer scrolls when favorites or devices make it longer than the screen.

**How do I select many files quickly?**
Long-press one item, then keep your finger down and drag across the list or grid: every item you pass over is added to the selection, like in a gallery app. Lift your finger to stop; the selection stays. Tapping items afterwards toggles them one by one, and the selection toolbar works as usual. A normal swipe still scrolls, and the drawer edge-swipe is paused only while a selection is active.

**Why do some files show a picture and others a placeholder icon?**
Images (JPG, PNG, WebP, GIF, SVG, HEIC/AVIF where the device supports them) and videos show a real thumbnail; videos use a frame from the middle of the file. Everything else, and any file that is broken, unreadable or unsupported, falls back to the normal type icon. Thumbnails are generated on demand, cached, and downsampled, so large files do not slow the list down. Files that are only readable through root/Shizuku are shown with their icon because the thumbnailer runs as the app.

**Can I copy paths wrapped in quotes?**
Yes. Turn on Settings → Files → **Quote copied paths** (off by default) and **Copy path** wraps each path in single quotes, for example `'/sdcard/Download/file.txt'`, which is handy for shell scripts and Termux. It applies to the browser, the archive explorer and History.

**Why are all the grid cells the same size?**
Turn on Settings → Files → **Equal grid cells** (off by default) and every grid cell gets the same size regardless of the name. Names are then shown on one line and long ones scroll every few seconds so you can still read them.

**Where does Extract put my files?**
Pressing **Extract** opens a dialog that asks for the destination: **Here** (the archive's own folder), **New folder** (a subfolder named after the archive), or **Choose folder** (pick any folder in the built-in picker). The same dialog takes a password if the archive needs one and has a **Delete archive after extraction** checkbox. If a file with the same name already exists in the destination, the usual conflict dialog (replace / skip / keep both) appears.

**What happens when I open a zip from another app with KArchiver?**
KArchiver jumps straight to the folder that contains the archive, so it is right there in context, and opens an action sheet for it: **Preview**, **Extract**, **Verify**, **Open with**, **Share**, **Properties** and **Copy path**. If the other app only hands over a content URI that cannot be mapped to a real path, the file is copied to the app cache first and the same sheet opens from there.

**What is the Recents tab?**
One list of the most recently changed files and folders from every mounted volume, newest first. It scans in the background with a folder budget and a time budget, shows progress while it runs, and stops early on very large storages. Tap a folder to open it in Files; tap a file to open it. Pull the refresh button in the top bar to scan again.

**Can I browse above internal storage, like /data, /vendor or another user?**
Yes, optionally, and it works like a normal file manager. Turn on Settings → Elevation → **Browse system paths** (off by default) and pick Root or Shizuku. A `..` (parent folder) entry then appears at the top of the file list, so you can climb from `/storage/emulated/0` up through `/storage/emulated` and `/storage` to `/`, and from there open `/data`, `/data/data`, `/vendor`, `/system`, `/data/media/<user>` and so on. Root can list and read all of it; Shizuku runs as the `shell` user, so SELinux blocks it from `/data`, `/data/data` and other users' storage, while some system paths are readable. Read-only partitions such as `/system` and `/vendor` cannot be written even as root without a remount or an overlay, and a locked user's encrypted storage stays unreadable. Files opened from system paths are copied to the app cache first, so the normal viewer can display them. The up arrow in the top bar only appears once you are above the volume root (at `/storage/emulated` and higher); inside a volume the top-left button stays the menu, and the `..` row in the list handles the rest.

**Why do new files not show up until I pull to refresh?**
By default the list is only reloaded on navigation or pull-to-refresh. Turn on Settings → Files → **Auto-refresh folder** (off by default) to watch the open folder and reload automatically when files are created, renamed, changed or deleted. It uses the system file watcher on the folder the app can read directly, so it covers normal storage and app-specific folders, but not SAF-backed views, other apps' `Android/data` directories, or root-only paths such as `/data`. Events are debounced (about half a second) so a large copy refreshes once instead of on every file, and watching stops when the app leaves the foreground.
**Why is there a notification during an operation?**
Long archive operations run in a foreground service, and Android requires a visible notification for those. It disappears when the operation finishes.

**How do I collect logs for a bug report?**
Open Settings → About and press **Save logs to Downloads/KArchiver**. It gathers the system log for this app (warnings and errors and above), the in-app Kotlin log, and the Rust log into one text file named `karchiver-logs-<timestamp>.txt` in `Downloads/KArchiver`. Nothing is uploaded; the file is written locally so you can attach it to an issue.

**Are the app and the Rust core licensed the same way?**
No. `app/` is GPL-3.0-only and `rust/` is Apache-2.0. See [LICENSE](LICENSE) and [rust/LICENSE](rust/LICENSE).
