package mihon.kit.model

import kotlinx.serialization.Serializable

/**
 * Metadata for a third-party extension repository (e.g., Keiyoushi).
 * Matches the Mihon `repo.json` specification.
 *
 * @property baseUrl The root HTTPS URL hosting the repository (e.g. `https://raw.githubusercontent.com/keiyoushi/extensions/repo`).
 * @property name Human-readable repository title.
 * @property shortName Optional abbreviated identifier for compact UI display.
 * @property website Optional website or project homepage URL.
 * @property signingKeyFingerprint Optional SHA-256 fingerprint used to verify repository authenticity.
 */
@Serializable
data class ExtensionRepo(
    val baseUrl: String,
    val name: String,
    val shortName: String? = null,
    val website: String? = null,
    val signingKeyFingerprint: String? = null,
)

/**
 * Raw JSON metadata structure of `repo.json` as served by extension repositories.
 *
 * @property meta Repository descriptor metadata containing name, website, and fingerprint.
 * @property index_v2 Optional path or URL pointing to the index JSON file.
 */
@Serializable
data class ExtensionRepoDto(
    val meta: ExtensionRepoMetaDto? = null,
    val index_v2: String? = null,
)

/**
 * Nested metadata block contained within [ExtensionRepoDto].
 *
 * @property name Display name of the extension repository.
 * @property shortName Optional abbreviated identifier.
 * @property website Optional homepage URL.
 * @property signingKeyFingerprint Optional signing key fingerprint string.
 */
@Serializable
data class ExtensionRepoMetaDto(
    val name: String,
    val shortName: String? = null,
    val website: String? = null,
    val signingKeyFingerprint: String? = null,
)

/**
 * An available extension entry in an online repository catalog (from `index.min.json`).
 *
 * @property name Display name of the extension.
 * @property pkg Android application package name of the extension APK.
 * @property apk Relative filename or absolute URL to download the APK.
 * @property lang Two-letter ISO language code or special code ("all").
 * @property code Numeric versionCode integer for update tracking.
 * @property version Version display string (e.g. "1.4.12").
 * @property nsfw Integer flag where `1` indicates NSFW / 18+ content and `0` indicates SFW.
 * @property sources List of sources bundled within this extension.
 * @property repoBaseUrl Base repository URL associated with this catalog entry.
 */
@Serializable
data class AvailableExtension(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int = 0,
    val sources: List<AvailableExtensionSource> = emptyList(),
    val repoBaseUrl: String = "",
) {
    /** True if this extension contains NSFW / 18+ content. */
    val isNsfw: Boolean get() = nsfw == 1

    /**
     * Resolves the full URL to download the extension APK.
     */
    val apkUrl: String
        get() = if (apk.startsWith("http://") || apk.startsWith("https://")) {
            apk
        } else {
            val base = repoBaseUrl.trimEnd('/')
            "$base/apk/$apk"
        }

    /**
     * Resolves the full URL for the extension's icon (following standard Mihon repo conventions).
     */
    val iconUrl: String
        get() {
            val base = repoBaseUrl.trimEnd('/')
            return "$base/icon/$pkg.png"
        }
}

/**
 * Source information declared in an available extension index entry.
 *
 * @property id Unique source ID string (optional in repository catalog).
 * @property name Human-readable name of the source.
 * @property lang Primary language code supported by the source.
 * @property baseUrl Base HTTP URL of the source's website.
 */
@Serializable
data class AvailableExtensionSource(
    val id: String? = null,
    val name: String,
    val lang: String? = null,
    val baseUrl: String? = null,
)
