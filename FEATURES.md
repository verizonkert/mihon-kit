# Mihon-Kit Features & Client Integration Guide

`mihon-kit` is a standalone Android library bringing the full power of the official **Mihon / Tachiyomi** manga ecosystem into any custom Android application.

This document details every major subsystem and feature available in `mihon-kit` that clients can integrate.

---

## Feature Matrix Overview

| Feature Category | Capability | Status in `mihon-kit` |
| :--- | :--- | :---: |
| **Extension Runtime** | In-memory APK loading via `PathClassLoader` | ✅ Ready |
| **Extension Repos** | Official Mihon `repo.json` & `index.min.json` catalog | ✅ Ready |
| **Cryptographic Security** | SHA-256 certificate fingerprint verification | ✅ Ready |
| **Package Installer** | Modern `PackageInstaller.Session`, Legacy Intent & Shizuku | ✅ Ready |
| **Silent Uninstaller** | 100% promptless uninstallation via Shizuku & Session API | ✅ Ready |
| **Networking & HTTP** | OkHttp 5 with Zstandard (Zstd), Brotli, and Cloudflare bypass | ✅ Ready |
| **JavaScript Engine** | Headless QuickJS C++ runtime for obfuscated source scrapers | ✅ Ready |
| **RxJava Coroutine Bridge** | Zero-leak bridge running RxJava 1.x sources via Coroutines | ✅ Ready |
| **Offline Downloads** | In-process Flow + persistent WorkManager background queue | ✅ Ready |
| **CBZ Packaging** | Auto-packs downloaded pages into `.cbz` with `ComicInfo.xml` | ✅ Ready |
| **Local Manga Source** | Built-in Source (`ID = 0L`) for `.cbz`, `.zip`, `.epub`, & folders | ✅ Ready |
| **Backup & Restore** | Full `.tachibk` Protobuf export & import with GZIP stream | ✅ Ready |
| **Compose Reader** | Webtoon continuous strip, Paged R-to-L, and Paged L-to-R | ✅ Ready |
| **Dual-Page Spreads** | Automatic landscape detection & 2-page manga spread stitching | ✅ Ready |
| **Image Filters** | Invert colors (night mode), Grayscale, Contrast & Brightness | ✅ Ready |
| **Border Cropping** | Dynamic black & white margin trimmer for scan panels | ✅ Ready |
| **Reading Navigation** | 60 FPS hands-free Auto-Scroll, Volume buttons & Smart tap zones | ✅ Ready |
| **Smart Updates** | `FetchInterval` release frequency prediction, backoff, and `fi>`/`nu<` search | ✅ Ready |
| **Download Automation** | Auto-download upcoming chapters, auto-delete read chapters, storage cache trimmer | ✅ Ready |

---

## 1. Extension Runtime & Dynamic Loading

Clients can load standard Mihon/Tachiyomi extension APKs without installing them as full Android apps:

* **Dynamic In-Memory Classloading**: Loads APK code and resources via `PathClassLoader`.
* **Zero Manifest Pollution**: Extensions execute in isolated sandboxes.
* **Automatic Injekt Dependency Injection**: Pre-wires `NetworkHelper`, `Json`, `Preferences`, and `JavaScriptEngine`.

```kotlin
// Load extension from local APK file
val extension = MihonKit.loadExtensionApk(context, apkFile)
val sources = extension.sources
```

---

## 2. Extension Repositories & Online Catalog

Clients can fetch online extensions from official or third-party repositories (such as Keiyoushi):

* **Repository Management**: Add, persist, and remove repo URLs (`repo.json` or `index.min.json`).
* **Extension Discovery**: Query catalogs, search by title, language, version, and filter NSFW content.
* **Direct APK Downloading**: Download APK packages directly from repository servers.

```kotlin
val repoService = MihonKit.getExtensionRepoService(context)

// Add repo & fetch extensions
val repo = repoService.addRepo("https://raw.githubusercontent.com/keiyoushi/extensions/repo")
val available = repoService.fetchAllAvailableExtensions()
val apkFile = repoService.downloadExtensionApk(available.first())
```

---

## 3. Cryptographic Signature Verification

Protects users against malicious or tampered third-party extension APKs:

* **SHA-256 Fingerprint Extraction**: Extracts X.509 signing certificates across Android versions (including Android 9+ `SigningInfo`).
* **Mismatch Detection**: Validates against the repository's registered fingerprint before loading or installing.

```kotlin
val result = MihonKit.verifyApkSignature(context, apkFile, repo.signingKeyFingerprint)
if (result is SignatureVerificationResult.Trusted) {
    MihonKit.loadExtensionApk(context, apkFile)
}
```

---

## 4. Physical Package Installation & Silent Uninstallation

For clients that want to install extensions as actual Android packages:

* **Mode 1: PackageInstaller.Session**: Modern streaming session installation.
* **Mode 2: Legacy Intent**: System dialog prompt.
* **Mode 3: Shizuku Silent Background Mode**: 100% silent background installation using Shizuku ADB/root privileges without UI prompts.
* **Uninstallation Suite**:
  - `uninstallViaShizuku(packageName)`: Completely silent background removal.
  - `uninstallViaSession(packageName)`: Modern PackageInstaller uninstallation session.
  - `uninstallViaLegacyIntent(packageName)`: Standard Android deletion dialog.

```kotlin
val installer = MihonKit.getPackageInstaller(context)

// Install silently with Shizuku
installer.installViaShizuku(apkFile).collect { status -> ... }

// Uninstall silently with Shizuku
installer.uninstallViaShizuku("eu.kanade.tachiyomi.extension.en.asurascans").collect { status -> ... }
```

---

## 5. Offline Chapter Downloads & WorkManager Queue

Reliable downloading of entire manga chapters:

* **In-Process Flow**: Reactive progress tracking (`DownloadStatus.Downloading(progress, downloaded, total)`).
* **WorkManager Background Queue**: Downloads survive app termination, memory pressure, and device reboots.
* **Network Constraints**: Configurable Wi-Fi only mode (`requiresWifi = true`).
* **Foreground System Notifications**: Displays persistent progress bars in Android notification tray.

```kotlin
val queueManager = MihonKit.getDownloadQueueManager(context)

val workId = queueManager.enqueue(
    sourceId = sourceId,
    manga = manga,
    chapter = chapter,
    requiresWifi = false
)
```

---

## 6. CBZ / Archive Packaging & ComicInfo.xml

Clients can package downloaded chapters into standard comic archives:

* **Automatic CBZ Conversion**: Combines chapter images into `.cbz` archives upon download completion.
* **Embedded `ComicInfo.xml`**: Writes standard ComicRack metadata (Series, Chapter Title, Number, Writer, Penciller, Genres, Summary, PageCount).
* **Direct Utility**: `MihonKit.packageChapterToCbz(chapterDir, targetCbzFile, manga, chapter)`.

```kotlin
// Download directly to .cbz archive with embedded metadata
MihonKit.downloadChapter(
    sourceId = sourceId,
    chapter = chapter,
    outputDir = folder,
    saveAsCbz = true,
    manga = manga
)
```

---

## 7. Built-in Local Manga Source (`LocalSource`)

Allows clients to read local comic files stored on the device with zero internet connection:

* **Source ID**: Dedicated built-in source (`LocalSource.ID = 0L`).
* **Archive Support**: Scans and reads `.cbz`, `.zip`, `.epub`, and raw image folders.
* **Metadata Extraction**: Reads `ComicInfo.xml` inside archives to automatically fill in author, artist, and synopsis.
* **Cover Art Extraction**: Automatically extracts cover images for library display.

```kotlin
val localSource = MihonKit.getLocalSource()
localSource.baseDir = File(context.getExternalFilesDir(null), "local_manga")

val mangas = localSource.getPopularManga(1).mangas
val pages = localSource.getPageList(chapter)
```

---

## 8. Full Backup & Restore (`.tachibk` Protobuf)

Official Mihon / Tachiyomi backup compatibility:

* **Binary Protobuf Stream**: Matches the official `.tachibk` schema using `@ProtoNumber` tag definitions.
* **GZIP Compression**: Exports and imports compressed archives.
* **Cross-App Compatibility**: Users can transfer their library, chapters, categories, read history, tracking entries, and extension repos directly between Mihon and your app.
* **SAF URI Support**: Directly integrates with Android Storage Access Framework (`Uri`).

```kotlin
val backupManager = MihonKit.getBackupManager(context)

// Export
backupManager.exportToFile(backup, file)

// Import & Restore
val restoredBackup = backupManager.importFromFile(file)
```

---

## 9. Advanced Compose Reader Viewer (`MihonReader`)

A complete, high-performance Jetpack Compose reader with low-level post-processing:

* **Viewer Modes**:
  - `ViewerMode.Webtoon`: Continuous vertical scrolling strip.
  - `ViewerMode.PagedRtoL`: Traditional Right-to-Left manga paging.
  - `ViewerMode.PagedLtoR`: Western Left-to-Right comic paging.
* **Dual-Page Spreads (`DualPageMode`)**:
  - Automatically combines two portrait pages into a side-by-side double spread on landscape devices and tablets.
  - Preserves single cover pages.
* **Image Processing Filters (`ReaderColorFilter`)**:
  - `Invert`: Night mode color inversion for white background pages.
  - `Grayscale`: Converts color or discolored scans to crisp monochrome.
  - `Contrast Boost`: Dynamically sharpens text and linework on faint scans.
* **Dynamic Border Cropping (`BorderCropProcessor`)**:
  - Detects and trims dead white and black margins around manga panels.
* **Hands-Free Auto-Scroll**:
  - Continuous 60 FPS vertical scrolling for Webtoon mode with floating play/pause controls.
* **Hardware Key & Tap Navigation**:
  - Volume buttons flip pages forward and backward.
  - 3-zone tap layout (Left/Right to turn, Center to toggle controls).
* **Anti-Leech Header Injection**:
  - Automatically injects `Referer` and `User-Agent` into Coil image requests to avoid HTTP 403 Forbidden errors.

```kotlin
@Composable
fun ReaderScreen(sourceId: Long, chapter: SChapter, pages: List<Page>) {
    MihonReader(
        sourceId = sourceId,
        pages = pages,
        chapterTitle = chapter.name,
        viewerMode = ViewerMode.Webtoon,
        dualPageMode = DualPageMode.AutoLandscapeDual,
        filterMode = ReaderFilterMode.None,
        volumeKeysNavigation = true,
        onClose = { ... }
    )
}
```

---

## 10. Core Scraping & Network Engine

* **Headless JavaScript Engine (`QuickJS`)**: Executes JavaScript challenges, dynamic token extraction, and DOM manipulation required by Cloudflare/DDoS protection scrapers.
* **Next-Gen Compression**: `okhttp-zstd` (Zstandard decompression) and Brotli.
* **RxJava 1.x Coroutines Bridge**: Runs legacy extension reactive observables on Kotlin Coroutines without thread leaks.

---

## 11. Smart Updates & Chapter Release Interval Prediction (`FetchInterval`)

Prevents server IP bans and rate-limiting by avoiding naive bulk updates:

* **Upload Delta Frequency Analysis**: Calculates average release frequency based on the last 5–10 chapter upload timestamps.
* **Dynamic Skipping**: Filters a library down to only manga whose calculated next fetch window has passed (`isDueForUpdate == true`).
* **Intelligent Backoff**: Applies exponential backoff multipliers when expected releases are overdue.
* **Search Operators**: Supports filtering manga libraries using `fi>` / `fetchinterval:` and `nu<` / `nextupdate:` search queries.

```kotlin
// 1. Calculate fetch interval for a manga
val result = MihonKit.calculateFetchInterval(
    chapters = mangaChapters,
    lastFetchTimestamp = lastCheckedMillis
)
println("Expected every ${result.intervalDays} days. Due for update: ${result.isDueForUpdate}")

// 2. Filter entire library to only items due for update
val dueManga = FetchInterval.filterDueForUpdate(
    items = userLibrary,
    getChapters = { it.chapters },
    getLastFetch = { it.lastChecked },
    getCurrentInterval = { it.fetchInterval }
)

// 3. Search library using Mihon search operators
val matches = MihonKit.matchesUpdateFilters(
    query = "fi>7 nu<3",
    fetchIntervalDays = result.intervalDays,
```

---

## 12. Automated Download Automation & Cache Rules

Automates offline reading management and disk hygiene matching Mihon preferences:

* **Auto-Download Next Chapters**: Automatically enqueues the next 1–5 unread chapters when a user begins reading.
* **Auto-Delete Read Chapters**: Deletes downloaded chapter directories or `.cbz` archives immediately upon completion or after 24 hours/7 days.
* **Storage Cache Trimmer**: Automatically prunes disk image caches older than a configurable threshold (e.g. 7 days) or when cache exceeds max size limits.

```kotlin
val automation = MihonKit.getDownloadAutomationManager(context)

// 1. Auto-download next 2 unread chapters when user starts reading
val enqueuedIds = automation.autoDownloadNextChapters(
    sourceId = sourceId,
    manga = currentManga,
    chapters = allChapters,
    currentChapter = activeChapter,
    count = 2,
    saveAsCbz = true // packs directly to .cbz with ComicInfo.xml
)

// 2. Auto-delete read chapter (Immediately or after 24 hours)
automation.autoDeleteReadChapter(
    chapterDirOrCbz = chapterFile,
    mode = AutoDeleteMode.Immediately
)

// 3. Trim storage image cache (prune older than 7 days or exceeding 500 MB)
val freedBytes = automation.trimStorageCache(
    olderThanMillis = 7 * 86_400_000L,
    maxSizeBytes = 500 * 1024 * 1024L
)
println("Cleaned up ${freedBytes / (1024 * 1024)} MB of old cached images!")
```


