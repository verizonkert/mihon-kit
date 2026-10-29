# MihonKit ⛩️

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)]()
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)]()

A lightweight, high-performance Android SDK to dynamically load and run **Mihon / Tachiyomi extension APKs** directly inside any Android application without requiring the Mihon app or system installation.

---

## 📦 Installation (No Authentication Required)

### Step 1: Add the GitHub Maven repository
In your root `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://raw.githubusercontent.com/verizonkert/mihon-kit/mvn-repo")
        }
    }
}
```

### Step 2: Add the dependency
In your app's `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.verizonkert.mihon-kit:mihon-kit:1.0.0")
}
```

### Step 3: Configure Android Permissions
In your `AndroidManifest.xml`, declare Internet permission and the `<queries>` block so your app can discover installed Mihon extensions:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />

    <queries>
        <intent>
            <action android:name="tachiyomi.extension" />
        </intent>
    </queries>
</manifest>
```

---

## ✨ Features Included

| Feature | Description |
| :--- | :--- |
| **Dynamic APK & Device Loading** | Load extensions directly from uninstalled APK files or scan already-installed extension packages on the device. |
| **Multi-Source & Multilingual** | Supports single-source and multi-source extensions (`SourceFactory`) across all languages (`en`, `ja`, `es`, `all`). |
| **Popular & Search Browsing** | Paged browsing for popular titles and keyword text search. |
| **Dynamic Filter UI** | Ready-made Jetpack Compose `ExtensionFilterBottomSheet` that automatically generates tri-state tag chips, dropdowns, checkboxes, and sort orders defined by each extension. |
| **Full Manga & Chapter Metadata** | Extracts title, synopsis, cover art, status (Ongoing, Completed, etc.), author, artist, upload dates, chapter numbers, and scanlators. |
| **Page & Image Resolution** | Handles dynamic second-stage page image resolution (`getImageUrl`) and OkHttp progress monitors. |
| **Anti-Leech Header Injection** | Exposes custom source `Headers` (e.g. `Referer`, custom `User-Agent`) to bypass hotlink blocking and HTTP 403 errors. |
| **Offline Chapter Downloader** | Built-in `ChapterDownloader` that downloads full chapters into any directory with partial-resume support (HTTP 206) and reactive Kotlin `Flow<DownloadStatus>` progress. |
| **Extension Preferences UI** | Renders native AndroidX `PreferenceScreen` (`ConfigurableSource`) in Compose for domain/mirror switching, image quality settings, and account logins. |

---

## 🚀 Quick Start Example

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 1. Initialize once in Application
        MihonKit.initialize(this)
    }
}
```

```kotlin
// 2. Load all extensions installed on device
val extensions = MihonKit.loadAllInstalledExtensions(context)
val source = extensions.first().sources.first()

// 3. Browse popular titles
val mangasPage = MihonKit.getPopularManga(source.id, page = 1)

// 4. Download a chapter
val downloadFolder = File(context.filesDir, "downloads/${manga.title}/${chapter.name}")
MihonKit.downloadChapter(source.id, chapter, downloadFolder).collect { status ->
    when (status) {
        is DownloadStatus.Downloading -> println("Progress: ${(status.progress * 100).toInt()}%")
        is DownloadStatus.Completed -> println("Saved to ${status.outputDir.absolutePath}")
        is DownloadStatus.Error -> println("Error: ${status.cause.message}")
    }
}
```

---

## 📖 Detailed Documentation

For a comprehensive guide covering all API methods, reader page loading, Coil integration, and Compose filter sheets, see **[USAGE.md](USAGE.md)**.

---

## 📱 Sample App

See the included **`tester-app`** module for a complete Jetpack Compose reference implementation demonstrating browsing, interactive filter bottom sheets, clickable author/tag search, chapter downloads, and extension settings.

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for details.
