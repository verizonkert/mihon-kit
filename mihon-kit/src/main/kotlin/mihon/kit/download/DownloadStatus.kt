package mihon.kit.download

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import java.io.File

/**
 * Status of a chapter download task.
 */
sealed interface DownloadStatus {
    data object Queued : DownloadStatus
    data class Downloading(val progress: Float, val downloadedPages: Int, val totalPages: Int) : DownloadStatus
    data class Completed(val outputDir: File, val pageFiles: List<File>) : DownloadStatus
    data class Error(val cause: Throwable) : DownloadStatus
}

/**
 * Callback or state holder for a single page download.
 */
data class PageDownloadProgress(
    val page: Page,
    val pageNumber: Int,
    val totalPages: Int,
    val bytesRead: Long,
    val totalBytes: Long,
    val progress: Float,
    val isComplete: Boolean,
    val error: Throwable? = null,
)
