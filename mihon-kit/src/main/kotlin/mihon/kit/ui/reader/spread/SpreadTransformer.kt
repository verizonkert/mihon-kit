package mihon.kit.ui.reader.spread

import eu.kanade.tachiyomi.source.model.Page

/**
 * Display strategy for handling pages in reader.
 */
enum class DualPageMode {
    /** Single page at a time */
    Single,
    /** Two pages side-by-side in landscape/tablets */
    DualPage,
    /** Auto: single page in portrait, dual page in landscape */
    AutoLandscapeDual,
}

/**
 * Represents a single reading viewport entry: either a single page or a side-by-side pair.
 */
sealed interface ReaderSpread {
    data class Single(val page: Page) : ReaderSpread
    data class Dual(val first: Page, val second: Page) : ReaderSpread
}

/**
 * Transforms a list of [Page]s into spreads according to [DualPageMode] and reading direction.
 */
object SpreadTransformer {

    /**
     * Groups pages into [ReaderSpread]s.
     * Keeps page 0 (cover) as single if [isFirstPageCover] is true.
     * In R-to-L (manga), right page comes first chronologically; in L-to-R, left page comes first.
     */
    fun createSpreads(
        pages: List<Page>,
        dualPage: Boolean,
        isRightToLeft: Boolean = true,
        isFirstPageCover: Boolean = true,
    ): List<ReaderSpread> {
        if (!dualPage || pages.size <= 1) {
            return pages.map { ReaderSpread.Single(it) }
        }

        val result = mutableListOf<ReaderSpread>()
        var index = 0

        // Keep first page (cover) solo if requested
        if (isFirstPageCover && pages.isNotEmpty()) {
            result.add(ReaderSpread.Single(pages[0]))
            index = 1
        }

        while (index < pages.size) {
            if (index + 1 < pages.size) {
                val p1 = pages[index]
                val p2 = pages[index + 1]
                if (isRightToLeft) {
                    // Manga R-to-L: Second page is on the left, first page is on the right
                    result.add(ReaderSpread.Dual(first = p2, second = p1))
                } else {
                    // Comic L-to-R: First page is on the left, second page is on the right
                    result.add(ReaderSpread.Dual(first = p1, second = p2))
                }
                index += 2
            } else {
                result.add(ReaderSpread.Single(pages[index]))
                index++
            }
        }

        return result
    }
}
