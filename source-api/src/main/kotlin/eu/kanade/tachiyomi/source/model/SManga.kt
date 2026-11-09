@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject
import java.io.Serializable

/**
 * Core domain representation of a manga or comic series provided by an extension source.
 */
interface SManga : Serializable {

    /** Relative or absolute URL identifier for the manga on the source. */
    var url: String

    /** Title of the manga. */
    var title: String

    /** Remote URL pointing to the manga's cover art image. */
    var thumbnail_url: String?

    /** Artist(s) credited for the manga illustration. */
    var artist: String?

    /** Author(s) or writer(s) credited for the manga story. */
    var author: String?

    /** Publication status code (e.g. [ONGOING], [COMPLETED], [LICENSED], [PUBLISHING_FINISHED], [CANCELLED], [ON_HIATUS]). */
    var status: Int

    /** Synopsis or description text. */
    var description: String?

    /** Comma-separated list of genres or tags. */
    var genre: String?

    /** Library update check strategy for this manga. */
    var update_strategy: UpdateStrategy

    /** True if full manga details have already been fetched and populated. */
    var initialized: Boolean

    /**
     * Extra metadata associated with the manga.
     *
     * The JSON object is not visible to users and intended for internal or source-specific
     * purposes. Apps may define their own namespaced keys (e.g., `"mihon.*"`) for sources to populate.
     *
     * This allows apps to attach and ask for custom information without affecting the visible
     * manga data.
     *
     * @since tachiyomix 1.6
     */
    var memo: JsonObject

    fun getGenres(): List<String>? {
        if (genre.isNullOrBlank()) return null
        return genre?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() }?.distinct()
    }

    fun copy() = create().also {
        it.url = url
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description
        it.genre = genre
        it.status = status
        it.thumbnail_url = thumbnail_url
        it.update_strategy = update_strategy
        it.initialized = initialized
        it.memo = memo
    }

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SManga {
            return SMangaImpl()
        }
    }
}
