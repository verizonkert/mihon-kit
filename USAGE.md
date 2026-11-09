# Detailed Usage Guide: MihonKit

This guide explains how to integrate and use every feature of **MihonKit** in your Android app.

---

## Table of Contents
1. [Installation](#installation)
2. [Initialization](#1-initialization)
3. [Loading Extensions](#2-loading-extensions)
4. [Browsing & Searching Manga](#3-browsing--searching-manga)
5. [Using Dynamic Extension Filters](#4-using-dynamic-extension-filters)
6. [Manga Details & Chapters](#5-manga-details--chapters)
7. [Reading Pages](#6-reading-pages)
8. [Downloading Chapters for Offline Reading](#7-downloading-chapters-for-offline-reading)
9. [Extension Preferences Screen (`ConfigurableSource`)](#8-extension-preferences-screen-configurablesource)

---

## Installation (Zero Token / 100% Free & Open)

Clients can add `mihon-kit` directly using GitHub without any personal access tokens (PAT) or login credentials.

### Step 1: Add Repository

In the client app's root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        
        // Option A: GitHub Raw Maven Repository (Zero Token, hosted on GitHub branch)
        maven {
            url = uri("https://raw.githubusercontent.com/verizonkert/mihon-kit/mvn-repo")
        }

        // Option B: Or via JitPack (Zero Token)
        maven(url = "https://www.jitpack.io") {
            content {
                includeGroup("com.github.verizonkert")
                includeGroup("com.github.mihon")
                includeGroup("com.github.gpanther")
                includeGroup("com.github.zhanghai.quickjs-java")
            }
        }
    }
}
```

> **Note**: If the project uses the older `allprojects { repositories { ... } }` syntax in root `build.gradle`, add the `maven` URL there.

---

### Step 2: Add Dependency

In the app module's `build.gradle.kts` (`app/build.gradle.kts`):

```kotlin
dependencies {
    // Core MihonKit library (replace <version> with release tag)
    implementation("com.github.verizonkert:mihon-kit:<version>")
}
```

#### Optional: Excluding Transitive Components
`mihon-kit` is already self-contained and clean (it does **not** include heavy icon packs). However, if you want to exclude any specific transitive dependencies or force your own versions:

```kotlin
dependencies {
    implementation("com.github.verizonkert:mihon-kit:<version>") {
        // Example: Exclude if your app already brings a different version
        exclude(group = "org.jsoup", module = "jsoup")
    }
}
```

---

### Step 3: Required Android Configuration

Ensure your `app/build.gradle.kts` has Java 11/17+ and desugaring (or minSdk 26+):

```kotlin
android {
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}
```

---

## 1. Initialization

In your `Application` class, call `MihonKit.initialize`:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Sets up Injekt dependency injection container and default networking
        MihonKit.initialize(this)
    }
}
```

*Optionally pass a custom OkHttpClient:*
```kotlin
MihonKit.initialize(context = this, customOkHttpClient = myCustomOkHttpClient)
```

---

## 2. Loading Extensions

### Option A: Scan & Load All Installed Extensions on Device
Automatically finds all installed Mihon/Tachiyomi extensions installed on the device:

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    val extensions: List<LoadedExtension> = MihonKit.loadAllInstalledExtensions(context)
    
    extensions.forEach { ext ->
        println("Extension: ${ext.name} (${ext.packageName})")
        ext.sources.forEach { source ->
            println("  -> Source: ${source.name} [ID: ${source.id}, Lang: ${source.lang}]")
        }
    }
}
```

### Option B: Load a Specific Installed Extension by Package Name
```kotlin
val extension = MihonKit.loadInstalledExtension(
    context = context,
    packageName = "eu.kanade.tachiyomi.extension.all.asmhentai"
)
```

### Option C: Load Directly from an APK File (No Installation Required)
```kotlin
val apkFile = File(context.filesDir, "extensions/ahottie.apk")
val loaded = MihonKit.loadExtensionApk(context, apkFile)
```

---

## 3. Browsing & Searching Manga

### Popular / Trending Manga
```kotlin
val mangasPage = MihonKit.getPopularManga(sourceId = sourceId, page = 1)
mangasPage.mangas.forEach { manga ->
    println("Title: ${manga.title}, Cover: ${manga.thumbnail_url}")
}
```

### Keyword Search
```kotlin
val results = MihonKit.searchManga(
    sourceId = sourceId,
    page = 1,
    query = "Solo Leveling"
)
```

---

## 4. Using Dynamic Extension Filters

Each extension supplies its own filter tree (tri-state tags, checkboxes, dropdowns, sort directions). Render the filter bottom sheet in Jetpack Compose:

```kotlin
@Composable
fun MangaBrowseScreen(sourceId: Long) {
    var showFilters by remember { mutableStateOf(false) }
    val source = MihonKit.getSource(sourceId) as? CatalogueSource
    var filterList by remember(sourceId) { mutableStateOf(source?.getFilterList() ?: FilterList()) }

    IconButton(onClick = { showFilters = true }) {
        Icon(Icons.Default.FilterList, contentDescription = "Filters")
    }

    if (showFilters) {
        ExtensionFilterBottomSheet(
            filters = filterList,
            onDismissRequest = { showFilters = false },
            onReset = {
                filterList = source?.getFilterList() ?: FilterList()
            },
            onApply = { updatedFilters ->
                filterList = updatedFilters
                // Perform search with custom filters:
                scope.launch {
                    val filtered = MihonKit.searchManga(
                        sourceId = sourceId,
                        page = 1,
                        query = "",
                        filters = updatedFilters
                    )
                }
            }
        )
    }
}
```

---

## 5. Manga Details & Chapters

### Fetching Updated Metadata & Chapter List
```kotlin
val update = MihonKit.getMangaUpdate(
    sourceId = sourceId,
    manga = selectedManga,
    fetchDetails = true,
    fetchChapters = true
)

val fullManga: SManga = update.manga
println("Synopsis: ${fullManga.description}")
println("Author: ${fullManga.author}")
println("Artist: ${fullManga.artist}")
println("Status: ${fullManga.status}") // SManga.ONGOING, SManga.COMPLETED, etc.

val chapters: List<SChapter> = update.chapters
chapters.forEach { chapter ->
    println("Chapter: ${chapter.name} | Number: ${chapter.chapter_number} | Scanlator: ${chapter.scanlator}")
}
```

---

## 6. Reading Pages

Fetch the list of pages and resolve individual image URLs:

```kotlin
// 1. Fetch page list
val pages: List<Page> = MihonKit.getPageList(sourceId, selectedChapter)

// 2. Resolve image URLs (some extensions require second-step URL resolution)
pages.forEach { page ->
    val finalImageUrl: String = MihonKit.getImageUrl(sourceId, page)
    println("Page ${page.number}: $finalImageUrl")
}

// 3. Get Anti-Leech / Referer Headers (pass to Coil/Glide to avoid HTTP 403 Forbidden)
val headers: okhttp3.Headers? = MihonKit.getSourceHeaders(sourceId)
```

---

## 7. Downloading Chapters for Offline Reading

Download full chapters directly into any folder with reactive progress tracking and resume support:

```kotlin
val outputFolder = File(context.filesDir, "downloads/${manga.title}/${chapter.name}")

MihonKit.downloadChapter(
    sourceId = sourceId,
    chapter = selectedChapter,
    outputDir = outputFolder
).collect { status ->
    when (status) {
        is DownloadStatus.Queued -> {
            println("Download queued...")
        }
        is DownloadStatus.Downloading -> {
            val percent = (status.progress * 100).toInt()
            println("Progress: $percent% (${status.downloadedPages}/${status.totalPages})")
        }
        is DownloadStatus.Completed -> {
            println("Downloaded ${status.pageFiles.size} pages to ${status.outputDir.absolutePath}")
        }
        is DownloadStatus.Error -> {
            println("Download failed: ${status.cause.message}")
        }
    }
}
```

### Download Directly as `.cbz` Archive (with ComicInfo.xml)

Just like Mihon's *"Save chapters as CBZ archives"* setting:

```kotlin
// 1. In-process download directly to .cbz
MihonKit.downloadChapter(
    sourceId = sourceId,
    chapter = selectedChapter,
    outputDir = outputFolder,
    saveAsCbz = true,
    manga = selectedManga, // writes series, author, genre into embedded ComicInfo.xml
).collect { status ->
    if (status is DownloadStatus.Completed) {
        println("Saved CBZ archive at: ${status.pageFiles.first().absolutePath}")
    }
}

// 2. Persistent WorkManager queue directly to .cbz
val workId = queueManager.enqueue(
    sourceId = sourceId,
    manga = selectedManga,
    chapter = selectedChapter,
    saveAsCbz = true,
)

// 3. Or package an already-downloaded directory anytime
val cbzFile = MihonKit.packageChapterToCbz(
    chapterDir = outputFolder,
    targetCbzFile = File(outputFolder.parentFile, "${selectedChapter.name}.cbz"),
    manga = selectedManga,
    chapter = selectedChapter,
    deleteSourceAfter = true,
)
```

### Persistent Background Queue (Survives App Kill & Reboots)

To download chapters in the background via Android Jetpack WorkManager with system notifications:

```kotlin
val queueManager = MihonKit.getDownloadQueueManager(context)

// 1. Enqueue a chapter download (sequenced automatically, survives app kills)
val workId = queueManager.enqueue(
    sourceId = sourceId,
    manga = selectedManga,
    chapter = selectedChapter,
    requiresWifi = false, // optionally set true to only download on Wi-Fi
    saveAsCbz = true // saves directly to .cbz archive with ComicInfo.xml
)

// 2. Observe download progress reactively
queueManager.getDownloadStatusFlow(workId).collect { status ->
    when (status) {
        is DownloadStatus.Downloading -> {
            println("Background Progress: ${(status.progress * 100).toInt()}% (${status.downloadedPages}/${status.totalPages})")
        }
        is DownloadStatus.Completed -> {
            println("Background download completed!")
        }
        is DownloadStatus.Error -> {
            println("Background download failed: ${status.cause.message}")
        }
        else -> {}
    }
}

// 3. Cancel a download or cancel all
queueManager.cancel(workId)
// queueManager.cancelAll()
```

---

## 8. Extension Preferences Screen (`ConfigurableSource`)

If an extension allows choosing server mirrors, domain switches, image quality, or entering credentials, render its native Android `PreferenceScreen`:

```kotlin
@Composable
fun ExtensionSettingsScreen(sourceId: Long) {
    ExtensionPreferencesScreen(sourceId = sourceId)
}
```

Or read and write preference values directly:
```kotlin
val prefs = MihonKit.getSourcePreferences(sourceId)
val currentMirror = prefs?.getString("override_base_url", null)
```

---

## 9. Extension Repositories & Online Catalog Fetcher

MihonKit includes an exact implementation of Mihon's Extension Repositories architecture. You can add external repos (such as Keiyoushi), fetch online extension catalogs (`index.min.json`), and download extension APKs:

```kotlin
val repoService = MihonKit.getExtensionRepoService(context)

// 1. Add an extension repository (e.g., Keiyoushi)
// Accepts repo base URL or direct links ending in /repo.json or /index.min.json
val repo = repoService.addRepo("https://raw.githubusercontent.com/keiyoushi/extensions/repo")
println("Added repo: ${repo.name} (Key: ${repo.signingKeyFingerprint})")

// 2. Fetch available extensions from all saved repositories
val availableExtensions: List<AvailableExtension> = repoService.fetchAllAvailableExtensions()

availableExtensions.forEach { ext ->
    println("${ext.name} [${ext.lang}] v${ext.version} (NSFW: ${ext.isNsfw})")
    println("APK URL: ${ext.apkUrl}")
    println("Icon URL: ${ext.iconUrl}")
}

// 3. Download the extension APK file
val apkFile = repoService.downloadExtensionApk(availableExtensions.first())

// 4. Verify cryptographic signature & load safely
val result = MihonKit.verifyApkSignature(context, apkFile, repo.signingKeyFingerprint)
when (result) {
    is SignatureVerificationResult.Trusted -> {
        println("Verified safe certificate: ${result.fingerprint}")
        val loadedExt = MihonKit.loadExtensionApk(context, apkFile, expectedSigningFingerprint = repo.signingKeyFingerprint)
        println("Loaded extension: ${loadedExt.name} with ${loadedExt.sources.size} sources")
    }
    is SignatureVerificationResult.Mismatch -> {
        println("WARNING: Untrusted extension! Expected ${result.expectedFingerprint} but got ${result.actualFingerprint}")
    }
```

---

## 10. Physical Package Installers (Mihon Installer Modes)

While `MihonKit` can load APKs dynamically in memory via `loadExtensionApk()`, you can also physically install APKs directly to Android matching Mihon's exact 3 installer modes:

```kotlin
val installer = MihonKit.getPackageInstaller(context)

// Mode 1: Modern Android PackageInstaller.Session (Mihon default)
// Streams APK bytes into an OS install session (silent updates on Android 12+)
installer.installViaSession(apkFile).collect { status ->
    when (status) {
        is InstallStatus.Installing -> println("Installing via Session...")
        is InstallStatus.Success -> println("Successfully installed on system!")
        is InstallStatus.Error -> println("Install error: ${status.message}")
    }
}

// Mode 2: Legacy Intent Installer
// Prompts the standard system Package Installer UI
installer.installViaLegacyIntent(apkFile)

// Mode 3: Shizuku Silent Background Installer
// Uses Shizuku ADB/root privileges to install completely in the background without UI prompts
if (installer.isShizukuAvailable()) {
    installer.installViaShizuku(apkFile).collect { status ->
        when (status) {
            is InstallStatus.Success -> println("Silently installed via Shizuku!")
            is InstallStatus.Error -> println("Shizuku error: ${status.message}")
            else -> {}
        }
    }
}

// -----------------------------------------------------------------------------
// Uninstallation (Session, Legacy & Shizuku Silent)
// -----------------------------------------------------------------------------

val targetPackageName = "eu.kanade.tachiyomi.extension.en.asurascans"

// 1. Shizuku 100% Silent Background Uninstaller (No user prompt)
if (installer.isShizukuAvailable()) {
    installer.uninstallViaShizuku(targetPackageName).collect { status ->
        when (status) {
            is UninstallStatus.Uninstalling -> println("Uninstalling silently...")
            is UninstallStatus.Success -> println("Silently uninstalled extension!")
            is UninstallStatus.Error -> println("Uninstall failed: ${status.message}")
        }
    }
}

// 2. Modern PackageInstaller Session Uninstaller
installer.uninstallViaSession(targetPackageName).collect { status ->
    when (status) {
        is UninstallStatus.Success -> println("Uninstalled via PackageInstaller!")
        is UninstallStatus.Error -> println("Failed: ${status.message}")
        else -> {}
    }
}

// 3. Legacy Intent Uninstaller (Standard Android confirmation prompt)
installer.uninstallViaLegacyIntent(targetPackageName)
```

---

## 11. Reader Viewers (Webtoon, Paged, Zoom)

`MihonKit` provides a ready-to-use Compose reader component matching Mihon's viewer modes:

```kotlin
@Composable
fun ChapterReaderScreen(sourceId: Long, chapter: SChapter, pages: List<Page>, onBack: () -> Unit) {
    MihonReader(
        sourceId = sourceId,
        pages = pages,
        chapterTitle = chapter.name,
        viewerMode = ViewerMode.Webtoon, // or ViewerMode.PagedRtoL / ViewerMode.PagedLtoR
        dualPageMode = DualPageMode.AutoLandscapeDual, // Auto-spreads 2 pages in landscape/tablets
        filterMode = ReaderFilterMode.None, // or ReaderFilterMode.Invert / ReaderFilterMode.Grayscale
        contrast = 1.0f, // boost contrast for faint scans
        volumeKeysNavigation = true, // flip pages using volume keys
        onClose = onBack,
        onPageSelected = { pageNumber ->
            println("Reading page: $pageNumber")
        }
    )
}
```

### Features included:
* **Webtoon Auto-Scroll**: Hands-free continuous automated vertical scrolling with a floating pause/resume toggle.
* **Dual-Page Spreads (`DualPageMode`)**: Automatically stitches consecutive manga pages into double spreads in landscape / tablet view while preserving single cover pages.
* **Image Processing Filters (`ReaderColorFilter`)**: Invert colors (night reading mode for white pages), Grayscale filter, and dynamic contrast boosting.
* **Dynamic Border Crop (`BorderCropProcessor`)**: Detects and automatically crops white and black margins around scanned manga panels.
* **Hardware Volume Key Navigation**: Turn pages back and forth with device volume buttons.
* **Smart Tap Zones**: Tap left/right to turn pages, tap center to toggle top/bottom overlays.
* **Pinch & Double-Tap Zoom**: Smooth transformation up to 5x with automatic centering.
* **Anti-Leech Header Injection**: Passes `Referer` and `User-Agent` to Coil so image requests never fail with HTTP 403.

---

## 12. Local Manga (.cbz, .zip, .epub as LocalSource)

`MihonKit` includes Mihon's exact built-in `LocalSource` (`ID = 0L`):

```kotlin
// 1. Get or customize LocalSource storage directory
val localSource = MihonKit.getLocalSource()
localSource.baseDir = File(context.getExternalFilesDir(null), "local_manga")

// 2. Discover local manga (folders containing .cbz, .zip, or image folders)
val mangasPage = localSource.getPopularManga(page = 1)
mangasPage.mangas.forEach { manga ->
    println("Local Manga: ${manga.title} (Cover: ${manga.thumbnail_url})")
}

// 3. Update details & discover chapters (.cbz, .zip, and subdirectories)
val update = localSource.getMangaUpdate(manga = mangasPage.mangas.first(), chapters = emptyList())
println("Author (from ComicInfo.xml): ${update.manga.author}")
println("Found ${update.chapters.size} local chapters!")

// 4. Read pages directly from .cbz archive or image directory
val pages = localSource.getPageList(update.chapters.first())
println("Extracted ${pages.size} pages ready for the reader!")
```

---

## 13. Full Backup & Restore (.tachibk Protobuf)

`MihonKit` supports exporting and importing standard `.tachibk` archives (GZIP-compressed Protocol Buffers) fully compatible with official Tachiyomi / Mihon:

```kotlin
val backupManager = MihonKit.getBackupManager(context)

// 1. Export library to .tachibk
val backup = Backup(
    backupManga = listOf(
        BackupManga(
            source = 123456789L,
            url = "/manga/one-piece",
            title = "One Piece",
            artist = "Eiichiro Oda",
            author = "Eiichiro Oda",
            chapters = listOf(
                BackupChapter(
                    url = "/manga/one-piece/chapter-1",
                    name = "Chapter 1: Romance Dawn",
                    chapterNumber = 1.0f,
                    read = true,
                    lastPageRead = 45L
                )
            ),
            categories = listOf(1L)
        )
    ),
    backupCategories = listOf(
        BackupCategory(name = "Reading", order = 0, id = 1L)
    ),
    backupExtensionRepos = listOf(
        BackupExtensionRepo(
            baseUrl = "https://raw.githubusercontent.com/mihonapp/extensions/repo",
            name = "Official Extensions",
            signingKeyFingerprint = "75470d0da07bf2ca1e23dbbe5c66b26cf8d43d31d4ad06a147e6ba2698993f35"
        )
    )
)

val backupFile = File(context.getExternalFilesDir(null), "backup.tachibk")
backupManager.exportToFile(backup, backupFile)
println("Exported .tachibk backup successfully!")

// 2. Validate .tachibk format before restore
val isValid = backupManager.validate(backupFile)
if (isValid) {
    // 3. Import from .tachibk
    val restoredBackup = backupManager.importFromFile(backupFile)
    println("Restored ${restoredBackup.backupManga.size} manga and ${restoredBackup.backupCategories.size} categories!")
}
```

