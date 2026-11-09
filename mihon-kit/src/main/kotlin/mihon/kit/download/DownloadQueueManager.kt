package mihon.kit.download

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

/**
 * High-level manager for persistent chapter download queues via Android Jetpack WorkManager.
 * Survives app kills, device reboots, and displays ongoing download notifications.
 */
class DownloadQueueManager(private val context: Context) {

    private val workManager: WorkManager = WorkManager.getInstance(context)

    companion object {
        /** Unique name used for WorkManager's sequential unique work chain. */
        const val UNIQUE_DOWNLOAD_WORK_NAME = "mihon_kit_chapter_downloads"

        /** Tag applied to all chapter download WorkRequests for bulk cancellation and querying. */
        const val TAG_DOWNLOAD = "mihon_kit_download"
    }

    /**
     * Enqueues a chapter download task into the persistent WorkManager queue.
     * Multiple calls will be executed sequentially (queued) so device bandwidth and battery are preserved.
     *
     * @param sourceId The source ID to download from.
     * @param manga The manga containing this chapter (for notification title & directory).
     * @param chapter The chapter to download.
     * @param outputDir Target directory for the chapter's downloaded page images (or temp directory if packaging to CBZ).
     * @param requiresWifi If true, download only starts when connected to unmetered Wi-Fi.
     * @param saveAsCbz If true, packages all pages into a `.cbz` archive with `ComicInfo.xml` upon completion.
     * @return The UUID of the WorkRequest to track status.
     */
    fun enqueue(
        sourceId: Long,
        manga: SManga,
        chapter: SChapter,
        outputDir: File = File(context.filesDir, "downloads/${sanitizeFilename(manga.title)}/${sanitizeFilename(chapter.name)}"),
        requiresWifi: Boolean = false,
        saveAsCbz: Boolean = false,
    ): UUID {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requiresWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(
            ChapterDownloadWorker.KEY_SOURCE_ID to sourceId,
            ChapterDownloadWorker.KEY_CHAPTER_URL to chapter.url,
            ChapterDownloadWorker.KEY_CHAPTER_NAME to chapter.name,
            ChapterDownloadWorker.KEY_CHAPTER_NUMBER to chapter.chapter_number,
            ChapterDownloadWorker.KEY_OUTPUT_DIR to outputDir.absolutePath,
            ChapterDownloadWorker.KEY_MANGA_TITLE to manga.title,
            ChapterDownloadWorker.KEY_MANGA_AUTHOR to manga.author,
            ChapterDownloadWorker.KEY_MANGA_ARTIST to manga.artist,
            ChapterDownloadWorker.KEY_MANGA_GENRE to manga.genre,
            ChapterDownloadWorker.KEY_SAVE_AS_CBZ to saveAsCbz,
        )

        val workRequest = OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(TAG_DOWNLOAD)
            .addTag("source_$sourceId")
            .addTag("chapter_${chapter.url.hashCode()}")
            .build()

        // Append to existing unique queue so downloads happen in orderly sequence
        workManager.enqueueUniqueWork(
            UNIQUE_DOWNLOAD_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest,
        )

        return workRequest.id
    }

    /**
     * Observes the status and progress of a specific download request as a reactive Coroutine Flow.
     */
    fun getDownloadStatusFlow(workId: UUID): Flow<DownloadStatus?> {
        return workManager.getWorkInfoByIdLiveData(workId).asFlow().map { workInfo ->
            if (workInfo == null) return@map null
            when (workInfo.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStatus.Queued
                WorkInfo.State.RUNNING -> {
                    val progress = workInfo.progress.getFloat("progress", 0f)
                    val downloaded = workInfo.progress.getInt("downloaded", 0)
                    val total = workInfo.progress.getInt("total", 0)
                    DownloadStatus.Downloading(progress, downloaded, total)
                }
                WorkInfo.State.SUCCEEDED -> {
                    DownloadStatus.Completed(File(""), emptyList())
                }
                WorkInfo.State.FAILED -> {
                    val error = workInfo.outputData.getString("error") ?: "Download failed"
                    DownloadStatus.Error(Exception(error))
                }
                WorkInfo.State.CANCELLED -> {
                    DownloadStatus.Error(Exception("Download was cancelled"))
                }
            }
        }
    }

    /**
     * Cancels a specific chapter download by its work UUID.
     */
    fun cancel(workId: UUID) {
        workManager.cancelWorkById(workId)
    }

    /**
     * Cancels all queued and active chapter downloads.
     */
    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG_DOWNLOAD)
    }

    /**
     * Replaces illegal file path characters (`\ / : * ? " < > |`) with underscores.
     */
    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }
}
