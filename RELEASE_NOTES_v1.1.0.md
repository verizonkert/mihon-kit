# MihonKit v1.1.0 ⛩️

We are excited to announce **MihonKit v1.1.0**! This milestone release transforms MihonKit into a complete, enterprise-grade Android SDK to integrate the entire Mihon / Tachiyomi manga ecosystem directly into any custom Android application.

---

### 📦 Quick Start (Tokenless Maven)

Add repository to your root `settings.gradle.kts`:
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

Add dependency to `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.verizonkert:mihon-kit:1.1.0")
}
```

---

## 🚀 What's New in v1.1.0

### 1. ⚙️ Extension Runtime & In-Memory Dynamic Loading
* **Dynamic Classloading**: Load and execute uninstalled extension APKs dynamically in memory via `PathClassLoader`.
* **Zero Manifest Contamination**: Run extensions in isolated application sandboxes.
* **Auto Injekt Wiring**: Automatically pre-wires `NetworkHelper`, `Json`, `Preferences`, and `JavaScriptEngine`.

### 2. 🌐 Extension Repositories & Keiyoushi Catalog Integration
* **Repository Manager (`ExtensionRepoService`)**: Add, list, and persist repo URLs supporting `repo.json` and `index.min.json`.
* **Extension Discovery**: Query catalogs, filter by language (`en`, `ja`, etc.), NSFW flags, and version tags.
* **Direct APK Download**: Download APK packages straight from remote repositories.

### 3. 🔐 Cryptographic Security & Anti-Malware Verification
* **SHA-256 Fingerprint Extraction**: Validates X.509 signing certificates (Android 9+ `SigningInfo` & legacy support).
* **Automated Mismatch Rejection**: Protects users from tampered or malicious extension packages before loading or installing.

### 4. 📲 Package Installer & Silent Uninstaller Suite
* **3 Installation Modes**: Modern `PackageInstaller.Session`, standard `Legacy Intent`, and **Shizuku silent background mode** (zero user prompts).
* **Uninstallation API**: Supports silent removal via Shizuku, `PackageInstaller` sessions, and native delete intents.

### 5. ⚡ Advanced Networking & QuickJS JavaScript Engine
* **Next-Gen OkHttp 5**: Zstandard (`zstd`), Brotli, and Cloudflare anti-bot handling.
* **Headless JavaScript Engine**: Embedded QuickJS C++ runtime to execute obfuscated JavaScript scrapers used by cloud-protected sources.
* **RxJava 1.x Coroutine Bridge**: Zero-leak, lifecycle-aware coroutine wrapper for legacy RxJava extensions.

### 6. 📥 Offline Chapter Downloader & WorkManager Engine
* **Dual Download Modes**: High-speed reactive `Flow<DownloadStatus>` in-process downloader and persistent background `ChapterDownloadWorker` via AndroidX `WorkManager`.
* **Auto-Resume & Progress Tracking**: HTTP 206 partial resume support, download speed metrics, and cancellation handling.
* **CBZ Auto-Packaging**: Automatically packs downloaded chapters into standard `.cbz` archives with `ComicInfo.xml` metadata.

### 7. 📁 Local Manga Source (`ID = 0L`)
* Seamlessly reads and indexes local comics and manga from device storage:
  * Supports `.cbz`, `.zip`, `.epub`, and loose image folders.
  * Auto-extracts covers and chapter lists.

### 8. 🔄 Backup & Restore Engine
* Full backup and restore engine fully compatible with Tachiyomi / Mihon `.tachibk` files.
* Supports Protobuf encoding/decoding compressed with GZIP streams.

### 9. 📖 Jetpack Compose Manga & Webtoon Reader
* **Multiple Reading Modes**:
  * Continuous vertical strip (Webtoons/Manhwa).
  * Right-to-Left paged reader (traditional Manga).
  * Left-to-Right paged reader (Western comics).
* **Dual-Page Spreads**: Automatically stitches dual-page manga spreads in landscape orientation.
* **Live Image Shaders & Filters**: Invert colors (night mode), Grayscale, Contrast & Brightness adjustments.
* **Border Crop Processor**: Dynamic black and white margin cropper to maximize screen usage.
* **Navigation**: 60 FPS hands-free Auto-Scroll, Volume-key page turns, and customizable 9-zone tap actions.

### 10. 🧠 Smart Updates & Automation Engine
* **Smart Update Prediction (`FetchInterval`)**: Calculates release intervals for ongoing titles to optimize update queries and minimize network traffic.
* **Filter Parser**: Advanced search query parser supporting `fi>` (fetch interval) and `nu<` (next update) queries.
* **Download Automation**: Auto-download upcoming unread chapters, auto-delete finished chapters, and cache trimmer.

---

## 🛠️ Developer & Publishing Improvements
* **Tokenless Maven Publishing**: Published directly to GitHub raw Maven (`mvn-repo` branch) with zero personal access tokens required.
* **Standardized Coordinate**: Clean client import `com.github.verizonkert:mihon-kit:1.1.0`.
* **100% Comprehensive KDocs**: Added full Kotlin documentation and API contracts across all public classes, interfaces, and methods.
* **Sample / Tester App Isolation**: Removed `tester-app` from library VCS tracking, ensuring pure library distribution.
