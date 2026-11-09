@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY

/**
 * Default standard implementation of [SChapter].
 */
class SChapterImpl : SChapter {

    override lateinit var url: String

    override lateinit var name: String

    override var chapter_number: Float = -1f

    override var scanlator: String? = null

    override var date_upload: Long = 0

    override var memo: JsonObject = JsonObject.EMPTY
}
