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

        val client = customOkHttpClient ?: OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .build()
        this.okHttpClient = client

        val appContext = context.applicationContext
        val app = appContext as? android.app.Application

        Injekt.addSingleton(appContext)
        if (app != null) {
            Injekt.addSingleton(app)
        }
        Injekt.addSingleton(client)

        // Extensions require NetworkHelper from core.common
        val preferenceStore = tachiyomi.core.common.preference.AndroidPreferenceStore(appContext)
        val networkPreferences = eu.kanade.tachiyomi.network.NetworkPreferences(preferenceStore, isDebugBuild = false)
        val networkHelper = eu.kanade.tachiyomi.network.NetworkHelper(
            scope = kotlinx.coroutines.GlobalScope,
            context = appContext,
            preferences = networkPreferences,
        )
        Injekt.addSingleton(networkHelper)

        // Extensions require Json serializer
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        Injekt.addSingleton(json)

        isInitialized = true
    }

    /**
     * Dynamically loads an extension APK file.
     *
     * @param context Application context.
     * @param apkFile The extension APK file on device storage.
     * @return The loaded extension metadata and sources.
     */
    suspend fun loadExtensionApk(context: Context, apkFile: File): LoadedExtension = withContext(Dispatchers.IO) {
        require(apkFile.exists() && apkFile.isFile) { "APK file does not exist: ${apkFile.absolutePath}" }

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
     * Downloads an entire chapter to the designated output folder, returning a Flow of download progress.
     */
    fun downloadChapter(
        sourceId: Long,
        chapter: SChapter,
        outputDir: java.io.File,
        pages: List<Page>? = null,
    ): kotlinx.coroutines.flow.Flow<mihon.kit.download.DownloadStatus> {
        return mihon.kit.download.ChapterDownloader.downloadChapter(sourceId, chapter, outputDir, pages)
    }
}



