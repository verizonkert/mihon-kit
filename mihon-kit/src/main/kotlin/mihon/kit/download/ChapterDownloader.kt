package mihon.kit.download

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import mihon.kit.MihonKit
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * ChapterDownloader handles sequentially or concurrently fetching chapter pages,
 * resolving image URLs, applying source headers, and saving pages to local storage.
 */
object ChapterDownloader {

    /**
     * Downloads an entire chapter to the given destination directory, emitting progress events.
     *
     * @param sourceId ID of the source.
     * @param chapter The chapter to download.
     * @param outputDir Target directory to store chapter page files.
     * @param pages Optional pre-fetched pages. If null or empty, [MihonKit.getPageList] will be called.
     * @return Flow of [DownloadStatus] tracking progress and completion.
     */
    fun downloadChapter(
        sourceId: Long,
        chapter: SChapter,
        outputDir: File,
        pages: List<Page>? = null,
    ): Flow<DownloadStatus> = flow {
        emit(DownloadStatus.Queued)

        val source = MihonKit.getSource(sourceId)
            ?: throw IllegalArgumentException("Source $sourceId not found")

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val pageList = if (!pages.isNullOrEmpty()) {
            pages
        } else {
            source.getPageList(chapter)
        }

        if (pageList.isEmpty()) {
            emit(DownloadStatus.Completed(outputDir, emptyList()))
            return@flow
        }

        val totalPages = pageList.size
        val downloadedFiles = mutableListOf<File>()
        val digitCount = totalPages.toString().length.coerceAtLeast(3)

        for ((index, page) in pageList.withIndex()) {
            val pageNum = index + 1
            val filenamePrefix = String.format(Locale.ENGLISH, "%0${digitCount}d", pageNum)

            // Look for existing downloaded image for this page
            val existingFile = outputDir.listFiles()?.firstOrNull {
                it.isFile && it.name.startsWith(filenamePrefix) && !it.name.endsWith(".tmp")
            }

            if (existingFile != null && existingFile.length() > 0) {
                downloadedFiles.add(existingFile)
                val progress = (index + 1).toFloat() / totalPages
                emit(DownloadStatus.Downloading(progress, index + 1, totalPages))
                continue
            }

            try {
                // Resolve image URL if needed
                if (page.imageUrl.isNullOrBlank() && source is HttpSource) {
                    page.imageUrl = source.getImageUrl(page)
                }

                // Download page bytes via HttpSource or fallback OkHttp call
                val pageFile = downloadPageFile(source, page, outputDir, filenamePrefix)
                downloadedFiles.add(pageFile)

                val progress = (index + 1).toFloat() / totalPages
                emit(DownloadStatus.Downloading(progress, index + 1, totalPages))
            } catch (t: Throwable) {
                emit(DownloadStatus.Error(t))
                return@flow
            }
        }

        emit(DownloadStatus.Completed(outputDir, downloadedFiles))
    }.flowOn(Dispatchers.IO)

    /**
     * Downloads an individual page to a file with progress.
     */
    private suspend fun downloadPageFile(
        source: eu.kanade.tachiyomi.source.Source,
        page: Page,
        targetDir: File,
        filenamePrefix: String,
    ): File = withContext(Dispatchers.IO) {
        val tmpFile = File(targetDir, "$filenamePrefix.tmp")
        val response = if (source is HttpSource) {
            source.getImage(page, if (tmpFile.exists()) tmpFile.length() else 0L)
        } else {
            val client = MihonKit.getOkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(page.imageUrl ?: page.url)
                .build()
            client.newCall(request).execute()
        }

        val extension = resolveImageExtension(response)
        val finalFile = File(targetDir, "$filenamePrefix.$extension")

        response.use { res ->
            if (!res.isSuccessful) {
                throw IllegalStateException("Failed to download image (HTTP ${res.code}): ${page.imageUrl}")
            }
            val body = res.body ?: throw IllegalStateException("Empty response body for page ${page.number}")
            
            // Append if 206 Partial Content, else overwrite
            val append = res.code == 206 && tmpFile.exists()
            FileOutputStream(tmpFile, append).use { outStream ->
                body.byteStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            }
        }

        if (finalFile.exists()) {
            finalFile.delete()
        }
        tmpFile.renameTo(finalFile)
        finalFile
    }

    private fun resolveImageExtension(response: Response): String {
        val mime = response.header("Content-Type")?.lowercase()
        return when {
            mime?.contains("image/png") == true -> "png"
            mime?.contains("image/jpeg") == true || mime?.contains("image/jpg") == true -> "jpg"
            mime?.contains("image/webp") == true -> "webp"
            mime?.contains("image/gif") == true -> "gif"
            mime?.contains("image/jxl") == true -> "jxl"
            mime?.contains("image/avif") == true -> "avif"
            else -> "jpg"
        }
    }
}
