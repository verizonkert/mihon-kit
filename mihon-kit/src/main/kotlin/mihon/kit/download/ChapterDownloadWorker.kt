package mihon.kit.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.source.model.SChapter
import mihon.kit.MihonKit
import java.io.File

/**
 * Background WorkManager [CoroutineWorker] for downloading manga chapters reliably.
 *
 * Provides resilient background execution that:
 * - Promotes execution to an Android foreground service with ongoing notification to prevent OS kill.
 * - Posts real-time progress updates through [DownloadStatus] and WorkManager progress data.
 * - Supports automatic retries and error reporting.
 * - Handles optional automatic packaging of chapter pages into `.cbz` archives upon completion.
 *
 * @param context The application context provided by WorkManager.
 * @param workerParams Parameters and configuration supplied to this worker instance.
 */
class ChapterDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        /** Input key for the unique source ID (Long). */
        const val KEY_SOURCE_ID = "source_id"

        /** Input key for the relative or absolute chapter URL (String). */
        const val KEY_CHAPTER_URL = "chapter_url"

        /** Input key for the human-readable chapter name (String). */
        const val KEY_CHAPTER_NAME = "chapter_name"

        /** Input key for the chapter number float (Float). */
        const val KEY_CHAPTER_NUMBER = "chapter_number"

        /** Input key for the destination folder path on disk (String). */
        const val KEY_OUTPUT_DIR = "output_dir"

        /** Input key for the manga title string (String). */
        const val KEY_MANGA_TITLE = "manga_title"

        /** Input key for the manga author string (String). */
        const val KEY_MANGA_AUTHOR = "manga_author"

        /** Input key for the manga artist string (String). */
        const val KEY_MANGA_ARTIST = "manga_artist"

        /** Input key for the manga genres string (String). */
        const val KEY_MANGA_GENRE = "manga_genre"

        /** Input key indicating whether completed downloads should be packed into `.cbz` (Boolean). */
        const val KEY_SAVE_AS_CBZ = "save_as_cbz"

        /** Identifier for the notification channel used for chapter downloads. */
        const val NOTIFICATION_CHANNEL_ID = "mihon_kit_downloads"

        /** User-facing name for the download notification channel. */
        const val NOTIFICATION_CHANNEL_NAME = "MihonKit Chapter Downloads"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val notificationId = id.hashCode()

    /**
     * Executes the background chapter download task.
     *
     * Extracts input parameters, initializes foreground notifications, invokes [ChapterDownloader.downloadChapter],
     * collects status updates, and returns [Result.success] or [Result.failure].
     */
    override suspend fun doWork(): Result {
        val sourceId = inputData.getLong(KEY_SOURCE_ID, -1L)
        val chapterUrl = inputData.getString(KEY_CHAPTER_URL) ?: return Result.failure()
        val chapterName = inputData.getString(KEY_CHAPTER_NAME) ?: "Chapter"
        val chapterNumber = inputData.getFloat(KEY_CHAPTER_NUMBER, -1f)
        val outputDirPath = inputData.getString(KEY_OUTPUT_DIR) ?: return Result.failure()
        val mangaTitle = inputData.getString(KEY_MANGA_TITLE) ?: "Manga"
        val mangaAuthor = inputData.getString(KEY_MANGA_AUTHOR)
        val mangaArtist = inputData.getString(KEY_MANGA_ARTIST)
        val mangaGenre = inputData.getString(KEY_MANGA_GENRE)
        val saveAsCbz = inputData.getBoolean(KEY_SAVE_AS_CBZ, false)

        if (sourceId == -1L) return Result.failure()

        val outputDir = File(outputDirPath)
        val chapter = SChapter.create().apply {
            url = chapterUrl
            name = chapterName
            chapter_number = chapterNumber
        }
        val manga = eu.kanade.tachiyomi.source.model.SManga.create().apply {
            title = mangaTitle
            author = mangaAuthor
            artist = mangaArtist
            genre = mangaGenre
        }

        createNotificationChannel()

        // Promote to foreground service notification so Android doesn't kill it
        try {
            setForeground(createForegroundInfo(mangaTitle, chapterName, 0, 0))
        } catch (_: Exception) {
            // ForegroundServiceStartNotAllowedException on Android 12+ if in background
        }

        var lastError: Throwable? = null

        try {
            ChapterDownloader.downloadChapter(
                sourceId = sourceId,
                chapter = chapter,
                outputDir = outputDir,
                saveAsCbz = saveAsCbz,
                manga = manga,
            ).collect { status ->
                when (status) {
                    is DownloadStatus.Queued -> {
                        setProgress(workDataOf("state" to "QUEUED"))
                    }
                    is DownloadStatus.Downloading -> {
                        val progressPercent = (status.progress * 100).toInt()
                        setProgress(
                            workDataOf(
                                "state" to "DOWNLOADING",
                                "progress" to status.progress,
                                "downloaded" to status.downloadedPages,
                                "total" to status.totalPages,
                            )
                        )
                        updateNotification(
                            mangaTitle = mangaTitle,
                            chapterName = chapterName,
                            downloaded = status.downloadedPages,
                            total = status.totalPages,
                        )
                    }
                    is DownloadStatus.Completed -> {
                        showCompletedNotification(mangaTitle, chapterName)
                    }
                    is DownloadStatus.Error -> {
                        lastError = status.cause
                    }
                }
            }
        } catch (e: Exception) {
            lastError = e
        }

        return if (lastError == null) {
            Result.success()
        } else {
            showErrorNotification(mangaTitle, chapterName, lastError.message ?: "Download failed")
            Result.failure(workDataOf("error" to (lastError.message ?: "Unknown error")))
        }
    }

    /**
     * Initializes the notification channel on Android 8.0 (API 26) or higher.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows progress of chapter downloads"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Creates a [ForegroundInfo] instance wrapping a progress notification for foreground service promotion.
     */
    private fun createForegroundInfo(
        mangaTitle: String,
        chapterName: String,
        downloaded: Int,
        total: Int,
    ): ForegroundInfo {
        val notification = buildProgressNotification(mangaTitle, chapterName, downloaded, total)
        return ForegroundInfo(notificationId, notification)
    }

    /**
     * Updates the ongoing download notification with updated page counts.
     */
    private fun updateNotification(
        mangaTitle: String,
        chapterName: String,
        downloaded: Int,
        total: Int,
    ) {
        val notification = buildProgressNotification(mangaTitle, chapterName, downloaded, total)
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Builds an ongoing progress [Notification] displaying manga title, chapter name, and page progress.
     */
    private fun buildProgressNotification(
        mangaTitle: String,
        chapterName: String,
        downloaded: Int,
        total: Int,
    ): Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(mangaTitle)
            .setContentText("$chapterName ($downloaded/$total)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(total, downloaded, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Displays a notification signifying successful chapter download completion.
     */
    private fun showCompletedNotification(mangaTitle: String, chapterName: String) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Download Complete: $mangaTitle")
            .setContentText(chapterName)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Displays an alert notification when a chapter download fails.
     */
    private fun showErrorNotification(mangaTitle: String, chapterName: String, errorMsg: String) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Download Failed: $mangaTitle")
            .setContentText("$chapterName: $errorMsg")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        notificationManager.notify(notificationId, notification)
    }
}
