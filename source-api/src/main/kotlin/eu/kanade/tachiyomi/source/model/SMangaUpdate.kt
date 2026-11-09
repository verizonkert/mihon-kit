package eu.kanade.tachiyomi.source.model

/**
 * Holds a paired manga and its corresponding list of parsed chapters.
 *
 * @property manga The [SManga] entity.
 * @property chapters List of [SChapter] entries retrieved for the manga.
 */
@Suppress("UNUSED")
class SMangaUpdate(val manga: SManga, val chapters: List<SChapter>)

