package eu.kanade.tachiyomi.source.model

/**
 * Represents a paginated slice of manga search or catalog browse results.
 *
 * @property mangas List of [SManga] items loaded on this page.
 * @property hasNextPage True if more pages are available to query from the source.
 */
class MangasPage(val mangas: List<SManga>, val hasNextPage: Boolean) {

    @Deprecated("MangasPage is now a regular class")
    operator fun component1(): List<SManga> = mangas

    @Deprecated("MangasPage is now a regular class")
    operator fun component2(): Boolean = hasNextPage

    @Deprecated("MangasPage is now a regular class")
    fun copy(
        mangas: List<SManga> = this.mangas,
        hasNextPage: Boolean = this.hasNextPage,
    ): MangasPage = MangasPage(
        mangas = mangas,
        hasNextPage = hasNextPage,
    )
}
