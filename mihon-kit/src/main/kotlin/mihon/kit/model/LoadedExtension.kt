package mihon.kit.model

import eu.kanade.tachiyomi.source.Source

/**
 * Represents a successfully loaded Mihon extension APK package and its contained sources.
 *
 * An extension can be loaded either as an installed Android application or dynamically
 * loaded from an isolated APK file via `PathClassLoader`.
 *
 * @property name Human-readable display name of the extension (e.g. "MangaDex").
 * @property packageName Android application package name of the extension APK (e.g. `eu.kanade.tachiyomi.extension.all.mangadex`).
 * @property versionName SemVer or version display string (e.g. "1.4.12").
 * @property versionCode Numeric Android versionCode integer for update detection and comparison.
 * @property libVersion The extension library API compatibility version (e.g. 1.4).
 * @property lang Two-letter ISO language code or special code (e.g. "en", "ja", "all").
 * @property isNsfw Whether the extension provides explicit or 18+ content.
 * @property sources The list of instantiated [Source] instances supplied by this extension.
 */
data class LoadedExtension(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val libVersion: Double,
    val lang: String,
    val isNsfw: Boolean,
    val sources: List<Source>,
)
