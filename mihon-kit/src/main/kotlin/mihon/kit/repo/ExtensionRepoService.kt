package mihon.kit.repo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import mihon.kit.MihonKit
import mihon.kit.model.AvailableExtension
import mihon.kit.model.AvailableExtensionSource
import mihon.kit.model.ExtensionRepo
import mihon.kit.model.ExtensionRepoDto
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Service to manage and fetch online extension catalogs and extension repositories,
 * exactly as implemented in Mihon.
 */
class ExtensionRepoService(
    private val context: Context,
    private val client: OkHttpClient = MihonKit.getOkHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    },
) {

    private val prefs = context.getSharedPreferences("mihon_extension_repos", Context.MODE_PRIVATE)
    private val reposKey = "saved_repos"

    /**
     * Resolves and normalizes repository URL to its root base URL.
     * E.g.
     * https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json -> https://raw.githubusercontent.com/keiyoushi/extensions/repo
     * https://raw.githubusercontent.com/keiyoushi/extensions/repo/repo.json -> https://raw.githubusercontent.com/keiyoushi/extensions/repo
     */
    fun normalizeRepoUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.endsWith("/")) {
            url = url.dropLast(1)
        }
        val suffixesToRemove = listOf(
            "/index.min.json",
            "/index.json",
            "/index.pb",
            "/repo.json",
            "/meta/repo.json"
        )
        for (suffix in suffixesToRemove) {
            if (url.endsWith(suffix, ignoreCase = true)) {
                url = url.substring(0, url.length - suffix.length)
                break
            }
        }
        return url
    }

    /**
     * Fetches metadata for a repository (from `repo.json` or `meta/repo.json`)
     * to verify the repository and get its display name, short name, and signingKeyFingerprint.
     */
    suspend fun fetchRepoDetails(rawUrl: String): ExtensionRepo = withContext(Dispatchers.IO) {
        val baseUrl = normalizeRepoUrl(rawUrl)

        // Try baseUrl/repo.json first, then baseUrl/meta/repo.json
        val repoJsonUrls = listOf(
            "$baseUrl/repo.json",
            "$baseUrl/meta/repo.json",
        )

        var lastException: Exception? = null

        for (targetUrl in repoJsonUrls) {
            try {
                val req = Request.Builder().url(targetUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body.string()
                        val dto = json.decodeFromString<ExtensionRepoDto>(body)
                        val meta = dto.meta
                        val name = meta?.name ?: meta?.shortName ?: baseUrl.substringAfterLast("/")
                        return@withContext ExtensionRepo(
                            baseUrl = baseUrl,
                            name = name,
                            shortName = meta?.shortName,
                            website = meta?.website,
                            signingKeyFingerprint = meta?.signingKeyFingerprint,
                        )
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        // If repo.json does not exist, fallback to extracting name from URL
        val fallbackName = baseUrl.substringAfterLast("/").ifEmpty { baseUrl }
        ExtensionRepo(
            baseUrl = baseUrl,
            name = fallbackName,
        )
    }

    /**
     * Retrieves all saved extension repositories.
     */
    fun getSavedRepos(): List<ExtensionRepo> {
        val rawSet = prefs.getStringSet(reposKey, emptySet()) ?: emptySet()
        return rawSet.mapNotNull { serialized ->
            runCatching { json.decodeFromString<ExtensionRepo>(serialized) }.getOrNull()
        }
    }

    /**
     * Adds an extension repository after verifying its details.
     */
    suspend fun addRepo(rawUrl: String): ExtensionRepo = withContext(Dispatchers.IO) {
        val repo = fetchRepoDetails(rawUrl)
        val current = getSavedRepos().filterNot { it.baseUrl.equals(repo.baseUrl, ignoreCase = true) }
        val updated = current + repo
        saveRepos(updated)
        repo
    }

    /**
     * Removes an extension repository.
     */
    fun removeRepo(baseUrl: String) {
        val normalized = normalizeRepoUrl(baseUrl)
        val current = getSavedRepos().filterNot { it.baseUrl.equals(normalized, ignoreCase = true) }
        saveRepos(current)
    }

    private fun saveRepos(repos: List<ExtensionRepo>) {
        val serializedSet = repos.map { json.encodeToString(ExtensionRepo.serializer(), it) }.toSet()
        prefs.edit().putStringSet(reposKey, serializedSet).apply()
    }

    /**
     * Fetches all available extensions from a given repository's `index.min.json`.
     *
     * @param repo The extension repository to query.
     */
    suspend fun fetchRepoCatalog(repo: ExtensionRepo): List<AvailableExtension> = withContext(Dispatchers.IO) {
        val baseUrl = repo.baseUrl.trimEnd('/')
        val indexUrls = listOf(
            "$baseUrl/index.min.json",
            "$baseUrl/index.json"
        )

        var lastError: Exception? = null

        for (indexUrl in indexUrls) {
            try {
                val req = Request.Builder().url(indexUrl).build()
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) return@use

                    val body = response.body.string()
                    val jsonElement = json.parseToJsonElement(body)
                    val array = jsonElement.jsonArray

                    return@withContext array.mapNotNull { element ->
                        runCatching {
                            val item = json.decodeFromJsonElement<AvailableExtension>(element)
                            item.copy(repoBaseUrl = baseUrl)
                        }.getOrNull()
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw lastError ?: IllegalStateException("Failed to fetch catalog from $baseUrl")
    }

    /**
     * Fetches available extensions from ALL saved repositories, deduplicating by package name
     * (favoring higher version code).
     */
    suspend fun fetchAllAvailableExtensions(): List<AvailableExtension> = withContext(Dispatchers.IO) {
        val repos = getSavedRepos()
        val allAvailable = mutableListOf<AvailableExtension>()

        for (repo in repos) {
            try {
                val items = fetchRepoCatalog(repo)
                allAvailable.addAll(items)
            } catch (e: Exception) {
                // Log and continue with other repos
            }
        }

        // Deduplicate: group by pkg, keep highest code
        allAvailable
            .groupBy { it.pkg }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.code } }
    }

    /**
     * Downloads an available extension APK file to local cache/directory for loading or installation.
     *
     * @param extension The available extension to download.
     * @param destinationDir Directory where APK should be saved.
     * @return The downloaded APK file.
     */
    suspend fun downloadExtensionApk(
        extension: AvailableExtension,
        destinationDir: File = File(context.cacheDir, "extension_apks")
    ): File = withContext(Dispatchers.IO) {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val destinationFile = File(destinationDir, extension.apk)
        val downloadUrl = extension.apkUrl

        val req = Request.Builder().url(downloadUrl).build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to download APK from $downloadUrl (HTTP ${response.code})")
            }

            val body = response.body
            body.byteStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        destinationFile
    }
}
