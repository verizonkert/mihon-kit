package mihon.kit.model

import eu.kanade.tachiyomi.source.Source

/**
 * Represents a successfully loaded Mihon extension.
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
