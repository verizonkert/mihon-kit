package eu.kanade.tachiyomi.source.model

import androidx.compose.runtime.Stable

/**
 * An immutable list wrapper containing interactive search filters for a source catalog.
 */
@Stable
data class FilterList(val list: List<Filter<*>>) : List<Filter<*>> by list {

    constructor(vararg fs: Filter<*>) : this(if (fs.isNotEmpty()) fs.asList() else emptyList())

    override fun equals(other: Any?): Boolean {
        return false
    }

    override fun hashCode(): Int {
        return list.hashCode()
    }
}
