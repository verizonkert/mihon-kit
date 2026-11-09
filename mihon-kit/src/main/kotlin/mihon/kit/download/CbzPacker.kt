package mihon.kit.download

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Handles packaging downloaded chapter pages and metadata into standard `.cbz` archives,
 * with optional ComicInfo.xml metadata generation compatible with Mihon and comic readers.
 */
object CbzPacker {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "jxl")

    /**
     * Packs all page images in [sourceDir] into [targetCbzFile].
     *
     * @param sourceDir Directory containing downloaded page images.
     * @param targetCbzFile Target .cbz file (e.g. "Chapter 1.cbz").
     * @param manga Optional manga metadata to write into ComicInfo.xml.
     * @param chapter Optional chapter metadata to write into ComicInfo.xml.
     * @param deleteSourceAfter If true, deletes [sourceDir] upon successful archive creation.
     * @return The created [File] representing the .cbz archive.
     */
    suspend fun packageToCbz(
        sourceDir: File,
        targetCbzFile: File,
        manga: SManga? = null,
        chapter: SChapter? = null,
        deleteSourceAfter: Boolean = true,
    ): File = withContext(Dispatchers.IO) {
        require(sourceDir.isDirectory) { "Source path must be a directory: ${sourceDir.absolutePath}" }

        val imageFiles = sourceDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS && !it.name.endsWith(".tmp") }
            ?.sortedBy { it.name }
            ?: emptyList()

        require(imageFiles.isNotEmpty()) { "No valid images found in directory: ${sourceDir.absolutePath}" }

        // Use a temporary file during packaging to prevent corrupted files on crash/interruption
        val tempCbzFile = File(targetCbzFile.parentFile ?: sourceDir.parentFile, "${targetCbzFile.name}.tmp")
        tempCbzFile.parentFile?.mkdirs()

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempCbzFile))).use { zipOut ->
                // Fast zip compression matching Mihon default
                zipOut.setLevel(Deflater.BEST_SPEED)

                // 1. Write ComicInfo.xml if metadata is provided
                if (manga != null || chapter != null) {
                    val comicInfoXml = generateComicInfoXml(manga, chapter, imageFiles.size)
                    val xmlEntry = ZipEntry("ComicInfo.xml")
                    zipOut.putNextEntry(xmlEntry)
                    zipOut.write(comicInfoXml.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }

                // 2. Write page images in order
                val buffer = ByteArray(32 * 1024)
                for (image in imageFiles) {
                    val entry = ZipEntry(image.name)
                    zipOut.putNextEntry(entry)
                    image.inputStream().use { input ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            zipOut.write(buffer, 0, bytesRead)
                        }
                    }
                    zipOut.closeEntry()
                }

                zipOut.flush()
            }

            // Replace existing target if any
            if (targetCbzFile.exists()) {
                targetCbzFile.delete()
            }
            if (!tempCbzFile.renameTo(targetCbzFile)) {
                tempCbzFile.copyTo(targetCbzFile, overwrite = true)
                tempCbzFile.delete()
            }

            // Clean up raw directory if requested
            if (deleteSourceAfter) {
                sourceDir.deleteRecursively()
            }

            targetCbzFile
        } catch (e: Exception) {
            tempCbzFile.delete()
            throw e
        }
    }

    /**
     * Generates a standard `ComicInfo.xml` metadata document compliant with ComicRack, Tachiyomi, and Mihon readers.
     *
     * @param manga Manga metadata (title, author, artist, genre, description).
     * @param chapter Chapter metadata (title, number, scanlator).
     * @param pageCount Total count of image pages contained within the archive.
     * @return Formatted XML string representation of ComicInfo.
     */
    fun generateComicInfoXml(
        manga: SManga?,
        chapter: SChapter?,
        pageCount: Int,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        sb.appendLine("""<ComicInfo xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">""")

        if (chapter?.name?.isNotBlank() == true) {
            sb.appendLine("  <Title>${escapeXml(chapter.name)}</Title>")
        }
        if (manga?.title?.isNotBlank() == true) {
            sb.appendLine("  <Series>${escapeXml(manga.title)}</Series>")
        }
        if (chapter?.chapter_number != null && chapter.chapter_number > 0f) {
            sb.appendLine("  <Number>${chapter.chapter_number}</Number>")
        }
        if (manga?.author?.isNotBlank() == true) {
            sb.appendLine("  <Writer>${escapeXml(manga.author!!)}</Writer>")
        }
        if (manga?.artist?.isNotBlank() == true) {
            sb.appendLine("  <Penciller>${escapeXml(manga.artist!!)}</Penciller>")
        }
        if (chapter?.scanlator?.isNotBlank() == true) {
            sb.appendLine("  <ScanInformation>${escapeXml(chapter.scanlator!!)}</ScanInformation>")
        }
        if (manga?.description?.isNotBlank() == true) {
            sb.appendLine("  <Summary>${escapeXml(manga.description!!)}</Summary>")
        }
        if (manga?.genre?.isNotBlank() == true) {
            sb.appendLine("  <Genre>${escapeXml(manga.genre!!)}</Genre>")
        }
        if (pageCount > 0) {
            sb.appendLine("  <PageCount>$pageCount</PageCount>")
        }

        sb.appendLine("</ComicInfo>")
        return sb.toString()
    }

    /**
     * Escapes standard XML special characters (`&`, `<`, `>`, `"`, `'`).
     */
    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
