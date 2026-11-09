package mihon.kit.download

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import java.io.File

/**
 * Status of a chapter download task.
 */
sealed interface DownloadStatus {
    /** Download task is queued in WorkManager or awaiting slot. */
    data object Queued : DownloadStatus

    /** Download task is actively fetching pages. */
    data class Downloading(val progress: Float, val downloadedPages: Int, val totalPages: Int) : DownloadStatus

    /** Download task has finished writing all pages and optional CBZ archive. */
    data class Completed(val outputDir: File, val pageFiles: List<File>) : DownloadStatus

    /** Download task encountered a fatal error. */
    data class Error(val cause: Throwable) : DownloadStatus
}

/**
 * Detailed progress event emitted during a single page image transfer.
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
