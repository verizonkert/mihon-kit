package mihon.kit.local

import android.content.Context
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Built-in [CatalogueSource] allowing reading and cataloging manga/comics directly from device storage.
 *
 * Fully compliant with Mihon's built-in Local Source specification (Source ID = 0L). Supports reading from
 * `.cbz`, `.zip`, `.epub` archives and plain filesystem directories.
 *
 * ### Expected Directory Structure:
 * ```text
 * localBaseDir/
 *   ├── Manga Title 1/
 *   │     ├── cover.jpg (optional thumbnail)
 *   │     ├── ComicInfo.xml (optional metadata)
 *   │     ├── Chapter 01.cbz (or .zip)
 *   │     └── Chapter 02/ (plain directory containing 01.jpg, 02.png)
 *   └── Manga Title 2.cbz
 * ```
 *
 * @param context Android application context for cache directory access.
 * @param baseDir Base root directory where local manga folders and archives reside (defaults to `filesDir/local_manga`).
 */
class LocalSource(
    private val context: Context,
    var baseDir: File = File(context.filesDir, "local_manga"),
) : CatalogueSource {

    companion object {
        /** Reserved source identifier for Mihon Local Source (always 0L). */
        const val ID: Long = 0L

        /** Display name for the local source. */
        const val NAME = "Local source"

        /** Language identifier for the local source. */
        const val LANG = "other"

        private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "avif", "gif", "bmp")
        private val SUPPORTED_ARCHIVE_EXTENSIONS = setOf("zip", "cbz", "epub")
    }

    override val id: Long = ID
    override val name: String = NAME
    override val lang: String = LANG
    override val supportsLatest: Boolean = true

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    /**
     * Lists local manga folders found in [baseDir].
     *
     * @param page Page index (local source delivers all manga on page 1 without pagination).
     * @return [MangasPage] containing discovered [SManga] entries.
     */
    override suspend fun getPopularManga(page: Int): MangasPage = withContext(Dispatchers.IO) {
        val mangas = getLocalMangaList()
        MangasPage(mangas, hasNextPage = false)
    }

    /**
     * Lists local manga folders sorted by disk modification time (newest first).
     */
    override suspend fun getLatestUpdates(page: Int): MangasPage = withContext(Dispatchers.IO) {
        val mangas = getLocalMangaList().sortedByDescending { File(it.url).lastModified() }
        MangasPage(mangas, hasNextPage = false)
    }

    /**
     * Filters local manga folders whose title, author, or genre matches [query].
     */
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = withContext(Dispatchers.IO) {
        val mangas = getLocalMangaList().filter {
            it.title.contains(query, ignoreCase = true) ||
                it.author?.contains(query, ignoreCase = true) == true ||
                it.genre?.contains(query, ignoreCase = true) == true
        }
        MangasPage(mangas, hasNextPage = false)
    }

    /**
     * Inspects the local manga directory to populate metadata from `ComicInfo.xml` and discover chapters.
     */
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = withContext(Dispatchers.IO) {
        val mangaDir = File(manga.url)
        val updatedManga = if (fetchDetails && mangaDir.exists()) {
            populateMangaMetadata(manga, mangaDir)
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters && mangaDir.exists()) {
            getChaptersForManga(mangaDir)
        } else {
            chapters
        }

        SMangaUpdate(updatedManga, updatedChapters)
    }

    /**
     * Resolves the page list for a chapter, whether it is a filesystem directory of images or an archive (`.cbz`, `.zip`, `.epub`).
     */
    override suspend fun getPageList(chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
        val chapterFile = File(chapter.url)
        if (!chapterFile.exists()) return@withContext emptyList()

        if (chapterFile.isDirectory) {
            // Folder chapter: list all image files sorted natural
            val imageFiles = chapterFile.listFiles { file ->
                file.isFile && file.extension.lowercase(Locale.ROOT) in SUPPORTED_IMAGE_EXTENSIONS
            }?.sortedBy { it.name } ?: emptyList()

            return@withContext imageFiles.mapIndexed { index, file ->
                Page(index = index, url = file.toURI().toString(), imageUrl = file.toURI().toString())
            }
        }

        val ext = chapterFile.extension.lowercase(Locale.ROOT)
        if (ext in SUPPORTED_ARCHIVE_EXTENSIONS) {
            // Archive chapter (.cbz, .zip, .epub): extract or read entries
            return@withContext extractArchivePages(chapterFile)
        }

        emptyList()
    }

    /**
     * Scans [baseDir] for directories representing manga titles.
     */
    private fun getLocalMangaList(): List<SManga> {
        if (!baseDir.exists() || !baseDir.isDirectory) return emptyList()

        val entries = baseDir.listFiles().orEmpty()
        return entries.filter { it.isDirectory }.map { dir ->
            SManga.create().apply {
                url = dir.absolutePath
                title = dir.name
                thumbnail_url = findCoverImage(dir)?.toURI()?.toString()
                status = SManga.COMPLETED
                initialized = true
            }
        }
    }

    /**
     * Scans a manga directory for chapters (.cbz, .zip, or subdirectories).
     */
    private fun getChaptersForManga(mangaDir: File): List<SChapter> {
        val files = mangaDir.listFiles().orEmpty()
        val chapterFiles = files.filter { file ->
            (file.isDirectory && containsImages(file)) ||
                (file.isFile && file.extension.lowercase(Locale.ROOT) in SUPPORTED_ARCHIVE_EXTENSIONS)
        }.sortedByDescending { it.name }

        return chapterFiles.mapIndexed { index, file ->
            SChapter.create().apply {
                url = file.absolutePath
                name = file.nameWithoutExtension
                date_upload = file.lastModified()
                chapter_number = (chapterFiles.size - index).toFloat()
            }
        }
    }

    private fun containsImages(dir: File): Boolean {
        return dir.listFiles { file ->
            file.isFile && file.extension.lowercase(Locale.ROOT) in SUPPORTED_IMAGE_EXTENSIONS
        }?.isNotEmpty() == true
    }

    private fun findCoverImage(dir: File): File? {
        val directCovers = listOf("cover.jpg", "cover.png", "cover.webp", "folder.jpg")
        for (name in directCovers) {
            val file = File(dir, name)
            if (file.exists() && file.isFile) return file
        }
        return dir.listFiles { file ->
            file.isFile && file.extension.lowercase(Locale.ROOT) in SUPPORTED_IMAGE_EXTENSIONS
        }?.firstOrNull()
    }

    /**
     * Reads `ComicInfo.xml` inside directory or .cbz if present to extract title, author, description, and genres.
     */
    private fun populateMangaMetadata(manga: SManga, mangaDir: File): SManga {
        var comicInfoFile = File(mangaDir, "ComicInfo.xml")
        if (!comicInfoFile.exists()) {
            comicInfoFile = File(mangaDir, "comicinfo.xml")
        }

        if (comicInfoFile.exists()) {
            runCatching {
                val xml = comicInfoFile.readText()
                val doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser())
                doc.selectFirst("Title")?.text()?.takeIf { it.isNotBlank() }?.let { manga.title = it }
                doc.selectFirst("Writer")?.text()?.takeIf { it.isNotBlank() }?.let { manga.author = it }
                doc.selectFirst("Penciller")?.text()?.takeIf { it.isNotBlank() }?.let { manga.artist = it }
                doc.selectFirst("Summary")?.text()?.takeIf { it.isNotBlank() }?.let { manga.description = it }
                doc.selectFirst("Genre")?.text()?.takeIf { it.isNotBlank() }?.let { manga.genre = it }
            }
        }
        return manga
    }

    /**
     * Extracts images from a .cbz / .zip / .epub into a cache folder for instant high-performance reading.
     */
    private fun extractArchivePages(archiveFile: File): List<Page> {
        val cacheFolder = File(context.cacheDir, "local_pages/${archiveFile.nameWithoutExtension}_${archiveFile.lastModified()}")
        if (!cacheFolder.exists()) {
            cacheFolder.mkdirs()
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase(Locale.ROOT) in SUPPORTED_IMAGE_EXTENSIONS }
                    .toList()

                for (entry in entries) {
                    val outputFile = File(cacheFolder, entry.name.substringAfterLast('/'))
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }

        val pages = cacheFolder.listFiles { file ->
            file.isFile && file.extension.lowercase(Locale.ROOT) in SUPPORTED_IMAGE_EXTENSIONS
        }?.sortedBy { it.name } ?: emptyList()

        return pages.mapIndexed { index, file ->
            Page(index = index, url = file.toURI().toString(), imageUrl = file.toURI().toString())
        }
    }
}
