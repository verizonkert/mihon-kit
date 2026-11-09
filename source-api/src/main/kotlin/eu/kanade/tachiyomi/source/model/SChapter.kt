@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject
import java.io.Serializable

/**
 * Core domain representation of an individual manga chapter or release.
 */
interface SChapter : Serializable {

    /** Relative or absolute URL identifier for the chapter on the source website. */
    var url: String

    /** Display name or title of the chapter (e.g. "Chapter 10: The Battle"). */
    var name: String

    /** Parsed floating-point chapter number (e.g. 10.5f). */
    var chapter_number: Float

    /** Name of the scanlation group or translator that released this chapter. */
    var scanlator: String?

    /** Unix timestamp in milliseconds indicating when this chapter was uploaded or published. */
    var date_upload: Long

    /**
     * Extra metadata associated with the chapter.
     *
     * The JSON object is not visible to users and intended for internal or source-specific
     * purposes. Apps may define their own namespaced keys (e.g., `"mihon.*"`) for sources to populate.
     *
     * This allows apps to attach and ask for custom information without affecting the visible
     * chapter data.
     *
     * @since tachiyomix 1.6
     */
    var memo: JsonObject

    fun copyFrom(other: SChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        chapter_number = other.chapter_number
        scanlator = other.scanlator
        memo = other.memo
    }

    companion object {
        fun create(): SChapter {
            return SChapterImpl()
        }
    }
}
