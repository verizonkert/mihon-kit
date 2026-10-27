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

## Installation

Add JitPack to your root `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        maven(url = "https://jitpack.io")
    }
}
```

Add the dependency to your app module's `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.yourusername:mihon-kit:1.0.0")
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
