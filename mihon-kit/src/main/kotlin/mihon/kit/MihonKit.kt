package mihon.kit

import android.content.Context
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.kit.loader.createExtensionClassLoader
import mihon.kit.model.LoadedExtension
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * MihonKit provides an interface to dynamically load, initialize, and execute
 * Mihon / Tachiyomi extension APKs in your Android application.
 */
object MihonKit {

    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_NAME = "tachiyomix.name"
    private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"

    private val sourceRegistry = ConcurrentHashMap<Long, Source>()
    private val extensionRegistry = ConcurrentHashMap<String, LoadedExtension>()

    private var isInitialized = false

    /**
     * Initializes the Injekt dependency injection container required by Mihon extensions.
     * Call this once in your Application.onCreate().
     */
    @Synchronized
    fun initialize(
        context: Context,
        customOkHttpClient: OkHttpClient? = null,
    ) {
        if (isInitialized) return

        val appContext = context.applicationContext
        val app = appContext as? android.app.Application

        Injekt.addSingleton(appContext)
        if (app != null) {
            Injekt.addSingleton(app)
        }

        // Extensions require NetworkHelper from core.common
        val preferenceStore = tachiyomi.core.common.preference.AndroidPreferenceStore(appContext)
        val networkPreferences = eu.kanade.tachiyomi.network.NetworkPreferences(preferenceStore, isDebugBuild = false)
        // If no DoH provider is set by the user, default to Cloudflare DoH (1) so DNS resolution works reliably on all emulators and networks
        if (networkPreferences.dohProvider.get() == -1) {
            networkPreferences.dohProvider.set(eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE)
        }
        val networkHelper = eu.kanade.tachiyomi.network.NetworkHelper(
            scope = kotlinx.coroutines.GlobalScope,
            context = appContext,
            preferences = networkPreferences,
        )
        Injekt.addSingleton(networkHelper)
        val finalClient = customOkHttpClient ?: networkHelper.client
        this.okHttpClient = finalClient
        Injekt.addSingleton(finalClient)

        // Extensions require Json serializer
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        Injekt.addSingleton(json)

        // Extensions require JavaScriptEngine (QuickJS headless runtime)
        val javaScriptEngine = eu.kanade.tachiyomi.network.JavaScriptEngine(appContext)
        Injekt.addSingleton(javaScriptEngine)

        // Built-in LocalSource (ID = 0L)
        val local = mihon.kit.local.LocalSource(appContext)
        localSource = local
        sourceRegistry[mihon.kit.local.LocalSource.ID] = local

        isInitialized = true
    }

    /**
     * Verifies the cryptographic SHA-256 certificate fingerprint of an extension APK file.
     *
     * @param context Application context.
     * @param apkFile The extension APK file.
     * @param expectedFingerprint Expected SHA-256 fingerprint from repo.json (optional).
     */
    fun verifyApkSignature(
        context: Context,
        apkFile: File,
        expectedFingerprint: String? = null,
    ): mihon.kit.security.SignatureVerificationResult {
        return mihon.kit.security.ExtensionSecurityValidator.verifyApk(context, apkFile, expectedFingerprint)
    }

    /**
     * Dynamically loads an extension APK file.
     *
     * @param context Application context.
     * @param apkFile The extension APK file on device storage.
     * @param expectedSigningFingerprint Optional expected SHA-256 signing fingerprint. If provided and does not match, an [IllegalStateException] is thrown.
     * @return The loaded extension metadata and sources.
     */
    suspend fun loadExtensionApk(
        context: Context,
        apkFile: File,
        expectedSigningFingerprint: String? = null,
    ): LoadedExtension = withContext(Dispatchers.IO) {
        require(apkFile.exists() && apkFile.isFile) { "APK file does not exist: ${apkFile.absolutePath}" }

        if (!expectedSigningFingerprint.isNullOrBlank()) {
            when (val check = verifyApkSignature(context, apkFile, expectedSigningFingerprint)) {
                is mihon.kit.security.SignatureVerificationResult.Mismatch -> {
                    throw SecurityException(
                        "Extension APK signature mismatch! Expected: ${check.expectedFingerprint}, Actual: ${check.actualFingerprint}"
                    )
                }
                is mihon.kit.security.SignatureVerificationResult.Error -> {
                    throw SecurityException("Failed to verify APK signature: ${check.message}")
                }
                is mihon.kit.security.SignatureVerificationResult.Trusted -> {
                    // Verified safe
                }
            }
        }

        val pkgManager = context.packageManager
        val flags = android.content.pm.PackageManager.GET_META_DATA or
            android.content.pm.PackageManager.GET_CONFIGURATIONS

        val pkgInfo = pkgManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: throw IllegalArgumentException("Failed to parse APK archive: ${apkFile.absolutePath}")

        val appInfo = pkgInfo.applicationInfo ?: throw IllegalArgumentException("Missing applicationInfo in APK")
        appInfo.sourceDir = apkFile.absolutePath
        appInfo.publicSourceDir = apkFile.absolutePath

        val metaData = appInfo.metaData ?: throw IllegalStateException("Missing <meta-data> in AndroidManifest.xml")

        val sourceClasses = metaData.getString(METADATA_SOURCE_CLASS)
            ?: throw IllegalStateException("Extension does not declare '$METADATA_SOURCE_CLASS'")

        val extName = metaData.getString(METADATA_NAME)
            ?: appInfo.loadLabel(pkgManager)?.toString()
            ?: pkgInfo.packageName

        val libVersion = metaData.getFloat(METADATA_EXTENSION_LIB).toDouble()
        val isNsfw = metaData.getInt(METADATA_NSFW, 0) == 1

        val classLoader = createExtensionClassLoader(
            dexPath = apkFile.absolutePath,
            librarySearchPath = appInfo.nativeLibraryDir,
            parent = context.classLoader,
        )

        val sources = sourceClasses.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith(".")) pkgInfo.packageName + it else it }
            .flatMap { className ->
                val clazz = Class.forName(className, false, classLoader)
                val instance = clazz.getDeclaredConstructor().newInstance()
                when (instance) {
                    is Source -> listOf(instance)
                    is SourceFactory -> instance.createSources()
                    else -> throw IllegalArgumentException("Class $className does not implement Source or SourceFactory")
                }
            }

        // Register sources to repository
        sources.forEach { sourceRegistry[it.id] = it }

        val loaded = LoadedExtension(
            name = extName,
            packageName = pkgInfo.packageName,
            versionName = pkgInfo.versionName ?: "1.0",
            versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            },
            libVersion = libVersion,
            lang = sources.firstOrNull()?.lang ?: "all",
            isNsfw = isNsfw,
            sources = sources,
        )

        extensionRegistry[pkgInfo.packageName] = loaded
        loaded
    }

    /**
     * Dynamically loads an extension already installed on the Android device by package name.
     *
     * @param context Application context.
     * @param packageName Package name of the installed extension (e.g. "eu.kanade.tachiyomi.extension.all.asmhentai")
     */
    suspend fun loadInstalledExtension(context: Context, packageName: String): LoadedExtension = withContext(Dispatchers.IO) {
        val pkgManager = context.packageManager
        val flags = android.content.pm.PackageManager.GET_META_DATA or
            android.content.pm.PackageManager.GET_CONFIGURATIONS

        val pkgInfo = try {
            pkgManager.getPackageInfo(packageName, flags)
        } catch (e: Exception) {
            throw IllegalArgumentException("Installed extension package not found: $packageName", e)
        }

        val appInfo = pkgInfo.applicationInfo ?: throw IllegalArgumentException("Missing applicationInfo")
        val metaData = appInfo.metaData ?: throw IllegalStateException("Missing meta-data in extension package")

        val sourceClasses = metaData.getString(METADATA_SOURCE_CLASS)
            ?: throw IllegalStateException("Extension does not declare '$METADATA_SOURCE_CLASS'")

        val extName = metaData.getString(METADATA_NAME)
            ?: appInfo.loadLabel(pkgManager)?.toString()
            ?: pkgInfo.packageName

        val libVersion = metaData.getFloat(METADATA_EXTENSION_LIB).toDouble()
        val isNsfw = metaData.getInt(METADATA_NSFW, 0) == 1

        val classLoader = createExtensionClassLoader(
            dexPath = appInfo.sourceDir,
            librarySearchPath = appInfo.nativeLibraryDir,
            parent = context.classLoader,
        )

        val sources = sourceClasses.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith(".")) pkgInfo.packageName + it else it }
            .flatMap { className ->
                val clazz = Class.forName(className, false, classLoader)
                val instance = clazz.getDeclaredConstructor().newInstance()
                when (instance) {
                    is Source -> listOf(instance)
                    is SourceFactory -> instance.createSources()
                    else -> throw IllegalArgumentException("Class $className does not implement Source or SourceFactory")
                }
            }

        sources.forEach { sourceRegistry[it.id] = it }

        val loaded = LoadedExtension(
            name = extName,
            packageName = pkgInfo.packageName,
            versionName = pkgInfo.versionName ?: "1.0",
            versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            },
            libVersion = libVersion,
            lang = sources.firstOrNull()?.lang ?: "all",
            isNsfw = isNsfw,
            sources = sources,
        )

        extensionRegistry[pkgInfo.packageName] = loaded
        loaded
    }

    /**
     * Scans and loads all Mihon / Tachiyomi extensions installed on the device.
     */
    suspend fun loadAllInstalledExtensions(context: Context): List<LoadedExtension> = withContext(Dispatchers.IO) {
        val pkgManager = context.packageManager
        val flags = android.content.pm.PackageManager.GET_META_DATA or
            android.content.pm.PackageManager.GET_CONFIGURATIONS

        val packages = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(android.content.pm.PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pkgManager.getInstalledPackages(flags)
        }

        packages.filter { pkg ->
            pkg.reqFeatures.orEmpty().any { it.name == "tachiyomi.extension" } ||
                pkg.applicationInfo?.metaData?.containsKey(METADATA_SOURCE_CLASS) == true
        }.mapNotNull { pkg ->
            runCatching { loadInstalledExtension(context, pkg.packageName) }.getOrNull()
        }
    }

    /**
     * Finds a registered Source by its unique ID.
     */
    fun getSource(sourceId: Long): Source? = sourceRegistry[sourceId]

    /**
     * Returns all registered Sources across all loaded extensions.
     */
    fun getAllSources(): List<Source> = sourceRegistry.values.toList()

    /**
     * Returns all loaded extensions.
     */
    fun getAllExtensions(): List<LoadedExtension> = extensionRegistry.values.toList()

    /**
     * Returns the [eu.kanade.tachiyomi.source.ConfigurableSource] if the source implements it.
     */
    fun getConfigurableSource(sourceId: Long): eu.kanade.tachiyomi.source.ConfigurableSource? {
        return getSource(sourceId) as? eu.kanade.tachiyomi.source.ConfigurableSource
    }

    /**
     * Returns the [android.content.SharedPreferences] for a configurable source.
     */
    fun getSourcePreferences(sourceId: Long): android.content.SharedPreferences? {
        val configurable = getConfigurableSource(sourceId) ?: return null
        return configurable.getSourcePreferences()
    }

    // =========================================================================
    // Convenience helper methods to interact with any Source from your app
    // =========================================================================

    suspend fun getPopularManga(sourceId: Long, page: Int = 1): MangasPage = withContext(Dispatchers.IO) {
        val source = getSource(sourceId)
            ?: throw IllegalArgumentException("Source $sourceId not found")
        source.getPopularManga(page)
    }

    suspend fun searchManga(sourceId: Long, page: Int = 1, query: String = "", filters: FilterList? = null): MangasPage = withContext(Dispatchers.IO) {
        val source = getSource(sourceId)
            ?: throw IllegalArgumentException("Source $sourceId not found")
        val filterList = filters ?: source.getFilterList()
        source.getSearchManga(page, query, filterList)
    }

    private lateinit var okHttpClient: OkHttpClient

    /**
     * Returns the OkHttpClient instance used by the library.
     */
    fun getOkHttpClient(): OkHttpClient {
        check(isInitialized) { "MihonKit is not initialized. Call MihonKit.initialize(context) first." }
        return okHttpClient
    }

    suspend fun getMangaUpdate(
        sourceId: Long,
        manga: SManga,
        chapters: List<SChapter> = emptyList(),
        fetchDetails: Boolean = true,
        fetchChapters: Boolean = true,
    ): SMangaUpdate = withContext(Dispatchers.IO) {
        val source = getSource(sourceId) ?: throw IllegalArgumentException("Source $sourceId not found")
        source.getMangaUpdate(manga, chapters, fetchDetails, fetchChapters)
    }

    suspend fun getPageList(sourceId: Long, chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
        val source = getSource(sourceId) ?: throw IllegalArgumentException("Source $sourceId not found")
        source.getPageList(chapter)
    }

    /**
     * Resolves the actual image URL for a [Page] if [Page.imageUrl] is empty.
     */
    suspend fun getImageUrl(sourceId: Long, page: Page): String = withContext(Dispatchers.IO) {
        val source = getSource(sourceId) ?: throw IllegalArgumentException("Source $sourceId not found")
        if (!page.imageUrl.isNullOrBlank()) {
            return@withContext page.imageUrl!!
        }
        if (source is eu.kanade.tachiyomi.source.online.HttpSource) {
            val resolved = source.getImageUrl(page)
            page.imageUrl = resolved
            resolved
        } else {
            page.imageUrl ?: page.url
        }
    }

    /**
     * Returns headers required to load/stream images from this source (e.g. anti-leech Referer / User-Agent).
     */
    fun getSourceHeaders(sourceId: Long): okhttp3.Headers? {
        val source = getSource(sourceId) ?: return null
        return (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.headers
    }

    /**
     * Executes the source's custom [HttpSource.getImage] method.
     * This ensures any custom cookies, headers, or progress trackers from the source are respected.
     */
    suspend fun getImageResponse(sourceId: Long, page: Page, existingSize: Long = 0L): okhttp3.Response = withContext(Dispatchers.IO) {
        val source = getSource(sourceId) ?: throw IllegalArgumentException("Source $sourceId not found")
        if (source is eu.kanade.tachiyomi.source.online.HttpSource) {
            source.getImage(page, existingSize)
        } else {
            val req = okhttp3.Request.Builder().url(page.imageUrl ?: page.url).build()
            getOkHttpClient().newCall(req).execute()
        }
    }

    /**
     * Downloads an entire chapter in-process to the designated output folder, returning a Flow of download progress.
     * Optionally packages the chapter into a `.cbz` archive with `ComicInfo.xml` when [saveAsCbz] is true.
     */
    fun downloadChapter(
        sourceId: Long,
        chapter: SChapter,
        outputDir: java.io.File,
        pages: List<Page>? = null,
        saveAsCbz: Boolean = false,
        manga: eu.kanade.tachiyomi.source.model.SManga? = null,
    ): kotlinx.coroutines.flow.Flow<mihon.kit.download.DownloadStatus> {
        return mihon.kit.download.ChapterDownloader.downloadChapter(sourceId, chapter, outputDir, pages, saveAsCbz, manga)
    }

    /**
     * Packages a directory of chapter page images into a standard `.cbz` archive with ComicInfo.xml metadata.
     */
    suspend fun packageChapterToCbz(
        chapterDir: java.io.File,
        targetCbzFile: java.io.File,
        manga: eu.kanade.tachiyomi.source.model.SManga? = null,
        chapter: SChapter? = null,
        deleteSourceAfter: Boolean = true,
    ): java.io.File {
        return mihon.kit.download.CbzPacker.packageToCbz(chapterDir, targetCbzFile, manga, chapter, deleteSourceAfter)
    }

    private var queueManager: mihon.kit.download.DownloadQueueManager? = null

    /**
     * Returns the persistent [mihon.kit.download.DownloadQueueManager] backed by Android WorkManager.
     * Manages background downloads that survive app termination and device reboots with system notifications.
     */
    fun getDownloadQueueManager(context: Context): mihon.kit.download.DownloadQueueManager {
        return queueManager ?: synchronized(this) {
            queueManager ?: mihon.kit.download.DownloadQueueManager(context.applicationContext).also {
                queueManager = it
            }
        }
    }

    private var downloadAutomationManager: mihon.kit.download.DownloadAutomationManager? = null

    /**
     * Returns the [mihon.kit.download.DownloadAutomationManager] to handle automatic pre-downloading of upcoming chapters,
     * auto-deleting read chapters, and automatic disk image cache trimming.
     */
    fun getDownloadAutomationManager(context: Context): mihon.kit.download.DownloadAutomationManager {
        return downloadAutomationManager ?: synchronized(this) {
            downloadAutomationManager ?: mihon.kit.download.DownloadAutomationManager(context.applicationContext).also {
                downloadAutomationManager = it
            }
        }
    }

    // =========================================================================
    // Online Extension Repositories & Catalog Fetcher (Mihon Extension Repos)
    // =========================================================================

    private var repoService: mihon.kit.repo.ExtensionRepoService? = null

    /**
     * Returns the [mihon.kit.repo.ExtensionRepoService] instance to fetch online extension catalogs,
     * add/remove repos, and download extension APKs.
     */
    fun getExtensionRepoService(context: Context): mihon.kit.repo.ExtensionRepoService {
        return repoService ?: synchronized(this) {
            repoService ?: mihon.kit.repo.ExtensionRepoService(
                context = context.applicationContext,
                client = getOkHttpClient(),
            ).also { repoService = it }
        }
    }

    // =========================================================================
    // Physical Package Installers (Mihon PackageInstaller, Legacy & Shizuku)
    // =========================================================================

    private var packageInstaller: mihon.kit.installer.ExtensionPackageInstaller? = null

    /**
     * Returns the [mihon.kit.installer.ExtensionPackageInstaller] to physically install extension APKs
     * onto Android using PackageInstaller.Session, Legacy Intent, or Shizuku silent installation.
     */
    fun getPackageInstaller(context: Context): mihon.kit.installer.ExtensionPackageInstaller {
        return packageInstaller ?: synchronized(this) {
            packageInstaller ?: mihon.kit.installer.ExtensionPackageInstaller(context.applicationContext).also {
                packageInstaller = it
            }
        }
    }

    private var localSource: mihon.kit.local.LocalSource? = null

    /**
     * Returns the built-in [mihon.kit.local.LocalSource] (ID = 0L) for reading local .cbz, .zip, .epub,
     * and directory-based manga.
     */
    fun getLocalSource(): mihon.kit.local.LocalSource {
        check(isInitialized) { "MihonKit is not initialized. Call MihonKit.initialize(context) first." }
        return localSource ?: throw IllegalStateException("LocalSource is not available.")
    }

    // =========================================================================
    // Backup & Restore (.tachibk Protobuf)
    // =========================================================================

    private var backupManager: mihon.kit.backup.BackupManager? = null

    /**
     * Returns the [mihon.kit.backup.BackupManager] to export and import `.tachibk` Protobuf archives
     * fully compatible with official Tachiyomi / Mihon.
     */
    fun getBackupManager(context: Context): mihon.kit.backup.BackupManager {
        return backupManager ?: synchronized(this) {
            backupManager ?: mihon.kit.backup.BackupManager(context.applicationContext).also {
                backupManager = it
            }
        }
    }

    // =========================================================================
    // Smart Updates & Chapter Release Interval Prediction (FetchInterval)
    // =========================================================================

    /**
     * Calculates the smart update interval and next expected fetch time for a manga based on historical chapter upload dates.
     * Prevents rate-limits and IP bans by avoiding unnecessary background network requests.
     */
    fun calculateFetchInterval(
        chapters: List<SChapter>,
        currentFetchInterval: Int = 0,
        lastFetchTimestamp: Long = 0L,
        currentTime: Long = System.currentTimeMillis(),
    ): mihon.kit.updates.FetchIntervalResult {
        return mihon.kit.updates.FetchInterval.calculate(
            chapters = chapters,
            currentFetchInterval = currentFetchInterval,
            lastFetchTimestamp = lastFetchTimestamp,
            currentTime = currentTime,
        )
    }

    /**
     * Evaluates whether a manga matches Mihon search query operators like `fi>7` or `nu<3`.
     */
    fun matchesUpdateFilters(
        query: String,
        fetchIntervalDays: Int,
        nextFetchTimestamp: Long,
    ): Boolean {
        return mihon.kit.updates.LibraryUpdateFilterParser.matchesFetchInterval(query, fetchIntervalDays) &&
                mihon.kit.updates.LibraryUpdateFilterParser.matchesNextUpdate(query, nextFetchTimestamp)
    }
}



