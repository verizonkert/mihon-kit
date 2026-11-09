package mihon.kit.download

import android.content.Context
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Strategy for automatically deleting read chapters to conserve device storage.
 */
enum class AutoDeleteMode {
    /** Never delete downloaded chapters automatically */
    Never,
    /** Delete chapter folder/.cbz immediately when marked read */
    Immediately,
    /** Delete chapter folder/.cbz after 24 hours of being read */
    After24Hours,
    /** Delete chapter folder/.cbz after 7 days of being read */
    After7Days,
}

/**
 * Handles automated chapter downloading ahead of reading, automatic deletion of read chapters,
 * and background disk image cache trimming matching official Mihon settings.
 */
class DownloadAutomationManager(private val context: Context) {

    private val queueManager = DownloadQueueManager(context)

    /**
     * Automatically enqueues the next [count] unread chapters when a user begins reading [currentChapter].
     *
     * @param sourceId Source ID of the manga.
     * @param manga The manga being read.
     * @param chapters Full list of chapters for this manga (in chronological or descending order).
     * @param currentChapter The chapter the user is currently reading.
     * @param count Number of upcoming unread chapters to pre-download (typically 1 to 5, default 2).
     * @param saveAsCbz Whether to package downloads as `.cbz` archives with `ComicInfo.xml`.
     * @param requiresWifi Whether to only download over unmetered Wi-Fi.
     * @param isChapterRead Optional lambda to check if a chapter is already read (default returns false).
     * @return List of [UUID]s of the enqueued WorkRequests.
     */
    fun autoDownloadNextChapters(
        sourceId: Long,
        manga: SManga,
        chapters: List<SChapter>,
        currentChapter: SChapter,
        count: Int = 2,
        saveAsCbz: Boolean = false,
        requiresWifi: Boolean = false,
        isChapterRead: (SChapter) -> Boolean = { false },
    ): List<UUID> {
        if (count <= 0) return emptyList()

        // Find index of the currently read chapter
        val currentIndex = chapters.indexOfFirst { it.url == currentChapter.url }
        if (currentIndex == -1) return emptyList()

        // Determine ordering: if index + 1 has a higher chapter number, list is chronological; else reverse
        val isChronological = if (chapters.size > 1 && currentIndex + 1 < chapters.size) {
            chapters[currentIndex + 1].chapter_number > currentChapter.chapter_number
        } else if (chapters.size > 1 && currentIndex > 0) {
            chapters[currentIndex].chapter_number > chapters[currentIndex - 1].chapter_number
        } else {
            true
        }

        // Extract upcoming unread chapters
        val candidates = if (isChronological) {
            chapters.drop(currentIndex + 1)
        } else {
            chapters.take(currentIndex).reversed()
        }

        val toDownload = candidates
            .filter { !isChapterRead(it) }
            .take(count)

        val enqueuedIds = mutableListOf<UUID>()
        for (nextChapter in toDownload) {
            val workId = queueManager.enqueue(
                sourceId = sourceId,
                manga = manga,
                chapter = nextChapter,
                requiresWifi = requiresWifi,
                saveAsCbz = saveAsCbz,
            )
            enqueuedIds.add(workId)
        }
        return enqueuedIds
    }

    /**
     * Automatically deletes a chapter's downloaded files or `.cbz` archive based on [mode].
     *
     * @param chapterDirOrCbz The directory or `.cbz` archive where the chapter is stored.
     * @param mode Deletion rule ([AutoDeleteMode.Immediately], [AutoDeleteMode.After24Hours], etc.).
     * @param readAtTimestamp Millis timestamp when the chapter was completed/marked read (default now).
     * @return True if deleted, false if skipped or postponed.
     */
    suspend fun autoDeleteReadChapter(
        chapterDirOrCbz: File,
        mode: AutoDeleteMode = AutoDeleteMode.Immediately,
        readAtTimestamp: Long = System.currentTimeMillis(),
    ): Boolean = withContext(Dispatchers.IO) {
        if (!chapterDirOrCbz.exists() || mode == AutoDeleteMode.Never) {
            return@withContext false
        }

        val now = System.currentTimeMillis()
        val shouldDelete = when (mode) {
            AutoDeleteMode.Never -> false
            AutoDeleteMode.Immediately -> true
            AutoDeleteMode.After24Hours -> (now - readAtTimestamp) >= 24 * 3600_000L
            AutoDeleteMode.After7Days -> (now - readAtTimestamp) >= 7 * 24 * 3600_000L
        }

        if (shouldDelete) {
            if (chapterDirOrCbz.isDirectory) {
                chapterDirOrCbz.deleteRecursively()
            } else {
                chapterDirOrCbz.delete()
            }
        } else {
            false
        }
    }

    /**
     * Cleans up disk cache images older than [olderThanMillis] (or exceeding [maxSizeBytes]).
     * Matching Mihon's automatic storage cache trimmer.
     *
     * @param cacheDirs Directories to inspect (default context cacheDir and Coil cache).
     * @param olderThanMillis Files older than this are deleted (default 7 days).
     * @param maxSizeBytes Optional maximum directory size. If exceeded, oldest files are pruned first.
     * @return Total bytes freed.
     */
    suspend fun trimStorageCache(
        cacheDirs: List<File> = listOf(
            context.cacheDir,
            File(context.cacheDir, "image_cache"),
            File(context.externalCacheDir ?: context.cacheDir, "image_cache"),
        ),
        olderThanMillis: Long = 7 * 86_400_000L,
        maxSizeBytes: Long = 500 * 1024 * 1024L, // 500 MB default max cache
    ): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        val now = System.currentTimeMillis()

        for (dir in cacheDirs) {
            if (!dir.exists() || !dir.isDirectory) continue

            val files = dir.walkTopDown()
                .filter { it.isFile && !it.name.endsWith(".tmp") }
                .toList()

            // 1. Delete files exceeding age threshold
            for (file in files) {
                if (now - file.lastModified() > olderThanMillis) {
                    val length = file.length()
                    if (file.delete()) {
                        freedBytes += length
                    }
                }
            }

            // 2. If directory still exceeds maxSizeBytes, prune oldest first
            val remainingFiles = dir.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.lastModified() }
                .toList()

            var currentSize = remainingFiles.sumOf { it.length() }
            for (file in remainingFiles) {
                if (currentSize <= maxSizeBytes) break
                val length = file.length()
                if (file.delete()) {
                    freedBytes += length
                    currentSize -= length
                }
            }
        }

        freedBytes
    }
}
