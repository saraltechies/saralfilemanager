# Saral File Manager

A simple, private file manager for Android by [Saral Techies](https://saraltechies.com/).
Free and open source (GPL-3.0). No ads, no account, and no internet permission: your files never leave your phone.

<!-- Add the Play badge once the listing is live:
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="80">](https://play.google.com/store/apps/details?id=com.saraltechies.saralfilemanager)
-->

## Features (1.1)

- **Home**: every storage volume (internal storage, SD card, USB drive) with its used and total space; tiles for Downloads, images, videos, audio, documents, apps, archives and APK files, each with its size and file count; quick access to Screenshots, Camera and Documents; bookmarked folders; recently changed files.
- **Free up space**: duplicate files (grouped; the oldest copy is kept and the others are ticked), files of 100 MB or more, screenshots older than 30 days, the Download folder, and apps not opened in 30 days. Nothing is deleted until you confirm.
- **Apps**: apps from the app drawer with their sizes (installer size, or app + data + cache with Usage access), last used date, sort by size, name, least used or install date; uninstall one or several (Android asks for each), open app info to clear cache or storage, save the installer to `Download/Saral Files/APKs` (split apps as `.apks`) or share it.
- **Browse** folder by folder with a breadcrumb path. List or grid view, sort by name (natural order: "Photo 2" before "Photo 10"), date, size or type, optionally reversed. Hidden files on request.
- **Photo and video previews** in lists and grids, made on the phone.
- **Copy, cut and paste** across folders and storages, **rename**, **delete** (with confirmation), **new folder** and **new file**. Long-press to select several items; Select all.
- **Zip and unzip**: compress any selection to a `.zip`; tap a zip to extract it into a new folder next to it. Entries that try to escape the folder ("zip slip") are skipped.
- **Search** the current folder and everything below it, as you type.
- **Details**: location, date, type, and the total size and file count of folders.
- **Open with** and **Share** through Android's normal app picker.
- Copies, moves and zips run in the background with a progress bar and **Cancel**; a cancelled copy leaves no half-written file.
- Shows as **Saral Files** under the icon. Launcher shortcuts (Free up space, Downloads, Images, Search), Material 3 with light and dark themes in the Saral Techies colours.
- Google Play rating and in-app update prompts (through the Play Store app, so still no internet permission).

## Permissions

| Android | Access | Why |
| --- | --- | --- |
| 11 and newer | **All files access** (`MANAGE_EXTERNAL_STORAGE`), switched on by the user in Settings | A file manager has to see and change every file and folder in shared storage. This is one of the uses Google Play allows. |
| 8 to 10 | `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` | Same, with the older permission dialog. |
| all | **Usage access** (`PACKAGE_USAGE_STATS`), optional, switched on by the user in Settings | Full app sizes (data and cache) and last-used dates on the Apps and Free up space screens. Without it, installer sizes are shown. |
| all | `REQUEST_DELETE_PACKAGES` | Opens Android's own uninstall prompt; Android asks the user to confirm each app. |

The Apps screen lists launcher apps through a `<queries>` entry for the launcher intent, so the app doesn't need `QUERY_ALL_PACKAGES`.

There is no `INTERNET` permission; the manifest removes it in case a library asks, and the release build fails if it appears.
Folders that Android keeps private to each app (`Android/data`, `Android/obb` on Android 11+) can't be opened by any file manager.
On Android 8 to 10, SD cards are read-only for apps that use the file path API, so copying to an SD card needs Android 11 or newer.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Navigation Compose · DataStore · plain `java.io` and `java.util.zip`.
minSdk 26 (Android 8.0), targetSdk 36, compileSdk 37.

```
app/src/main/java/com/saraltechies/saralfilemanager/
├── files/   # FileJob (copy/move/delete/zip/extract, pure JVM and unit-tested), listing, search, MediaStore categories, operations
├── data/    # DataStore settings (sort, view, hidden files, bookmarks), review policy
├── ui/      # Compose: home, folder browser, categories, shared list/dialog components, theme
└── util/    # FileProvider intents, storage access, thumbnails, Play Store, shortcuts
```

## Build

Requirements: JDK 17 and the Android SDK (open this folder in Android Studio).

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Every push and pull request runs the tests and builds a debug APK on GitHub Actions (download it from the run's **Artifacts**).

## Release (maintainers)

1. Raise `versionCode` (+1) and `versionName` in `app/build.gradle.kts` and push to `main`.
2. Push a tag `vX.Y.Z`, or run `gh workflow run android.yml -f release=true`. The workflow signs the build with the upload key stored in the repository secrets (`SARALFM_KEYSTORE_BASE64`, `SARALFM_KEYSTORE_PASSWORD`, `SARALFM_KEY_ALIAS`, `SARALFM_KEY_PASSWORD`).
3. Upload `app-release.aab` and `mapping.txt` from the run's artifacts to Play Console.

Store listing text, graphics, the privacy policy and the Play Console answers are in [`store/`](store).

## Contributing

Bug reports, ideas and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md), and report security problems privately as described in [SECURITY.md](SECURITY.md).

## License

Copyright © 2026 Saral Techies.

Saral File Manager is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License
as published by the Free Software Foundation, version 3 or (at your option) any later version. See [LICENSE](LICENSE).

The "Saral File Manager" and "Saral Techies" names and the app icon are trademarks of Saral Techies and aren't covered by the license.
If you publish a modified version, please give it a different name and icon.
