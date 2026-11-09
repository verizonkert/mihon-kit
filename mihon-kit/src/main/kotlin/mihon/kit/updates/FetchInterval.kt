package mihon.kit.updates

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Result of the fetch interval calculation.
 *
 * @param intervalDays Calculated expected days between chapters (e.g. 7 for weekly, 30 for monthly).
 *                     Negative values denote manual user override in Mihon.
 * @param nextFetchTimestamp Milliseconds timestamp when the next chapter check is scheduled.
 * @param isDueForUpdate Whether the current system time is equal to or past [nextFetchTimestamp].
 */
data class FetchIntervalResult(
    val intervalDays: Int,
    val nextFetchTimestamp: Long,
    val isDueForUpdate: Boolean,
)

/**
 * Intelligent Smart Update calculation engine matching Mihon's `FetchInterval` domain interactor.
 *
 * Dynamically computes when each library entry should next be checked for new chapters by analyzing
 * release deltas over past chapter upload timestamps.
 *
 * Key features:
 * - Prevents source IP bans and rate-limiting by skipping manga that aren't yet due.
 * - Supports exponential backoff if a chapter release is missed past its expected date.
 * - Honors negative intervals as manual user-defined fetch overrides (e.g. `-1` = force daily, `-7` = force weekly).
 */
object FetchInterval {

    /** Default fallback fetch interval in days when insufficient chapter upload history exists. */
    const val DEFAULT_INTERVAL_DAYS = 7

    /** Minimum allowable calculated interval in days (daily). */
    const val MIN_INTERVAL_DAYS = 1

    /** Maximum allowable calculated interval in days (up to 120 days for slow or quarterly releases). */
    const val MAX_INTERVAL_DAYS = 120

    /** Maximum number of recent chapter release dates sampled for interval calculation. */
    private const val MAX_CHAPTER_SAMPLE_SIZE = 10

    /** Multiplier applied to increase interval when an expected release date is missed. */
    private const val BACKOFF_MULTIPLIER = 1.4f

    /**
     * Calculates the smart update interval and next expected fetch time for a manga based on chapter upload history.
     *
     * @param chapters List of chapters (ordered newest to oldest or vice versa).
     * @param currentFetchInterval The current stored interval for the manga (0 = uncalculated).
     * @param lastFetchTimestamp Timestamp when the manga was last checked for updates.
     * @param currentTime Current epoch millis (default system current millis).
     * @param zoneId Timezone for day boundary rounding.
     * @return [FetchIntervalResult] indicating the interval and whether an update should run or be skipped.
     */
    fun calculate(
        chapters: List<SChapter>,
        currentFetchInterval: Int = 0,
        lastFetchTimestamp: Long = 0L,
        currentTime: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): FetchIntervalResult {
        // If user manually set a negative interval, respect it as a manual override
        if (currentFetchInterval < 0) {
            val days = currentFetchInterval.absoluteValue
            val nextFetch = if (lastFetchTimestamp > 0L) {
                lastFetchTimestamp + days * 86_400_000L
            } else {
                currentTime
            }
            return FetchIntervalResult(
                intervalDays = currentFetchInterval,
                nextFetchTimestamp = nextFetch,
                isDueForUpdate = currentTime >= nextFetch,
            )
        }

        // Filter valid upload dates
        val uploadDates = chapters
            .map { it.date_upload }
            .filter { it > 0L }
            .sortedDescending()
            .take(MAX_CHAPTER_SAMPLE_SIZE)

        val calculatedInterval = if (uploadDates.size >= 3) {
            // Delta calculation between consecutive uploads
            val deltas = mutableListOf<Long>()
            for (i in 0 until uploadDates.size - 1) {
                val deltaDays = ChronoUnit.DAYS.between(
                    Instant.ofEpochMilli(uploadDates[i + 1]).atZone(zoneId).toLocalDate(),
                    Instant.ofEpochMilli(uploadDates[i]).atZone(zoneId).toLocalDate(),
                ).absoluteValue
                if (deltaDays > 0) {
                    deltas.add(deltaDays)
                }
            }

            if (deltas.isNotEmpty()) {
                val averageDays = deltas.average().roundToInt()
                averageDays.coerceIn(MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS)
            } else {
                DEFAULT_INTERVAL_DAYS
            }
        } else {
            // Insufficient historical upload dates fallback
            if (currentFetchInterval > 0) currentFetchInterval else DEFAULT_INTERVAL_DAYS
        }

        // Check if an expected update was missed (Exponential Backoff)
        val latestUpload = uploadDates.firstOrNull() ?: lastFetchTimestamp
        val daysSinceLastUpload = if (latestUpload > 0L) {
            ChronoUnit.DAYS.between(
                Instant.ofEpochMilli(latestUpload).atZone(zoneId).toLocalDate(),
                Instant.ofEpochMilli(currentTime).atZone(zoneId).toLocalDate(),
            ).coerceAtLeast(0L)
        } else {
            0L
        }

        val effectiveInterval = if (daysSinceLastUpload > calculatedInterval * 2) {
            // Exponential backoff if release is late
            (calculatedInterval * BACKOFF_MULTIPLIER).roundToInt().coerceAtMost(MAX_INTERVAL_DAYS)
        } else {
            calculatedInterval
        }

        val nextFetch = if (lastFetchTimestamp > 0L) {
            lastFetchTimestamp + effectiveInterval * 86_400_000L
        } else {
            currentTime
        }

        return FetchIntervalResult(
            intervalDays = effectiveInterval,
            nextFetchTimestamp = nextFetch,
            isDueForUpdate = currentTime >= nextFetch,
        )
    }

    /**
     * Filters a library list down to only items that are actually due for updates,
     * skipping manga that haven't reached their scheduled fetch window.
     */
    fun <T> filterDueForUpdate(
        items: List<T>,
        getChapters: (T) -> List<SChapter>,
        getLastFetch: (T) -> Long,
        getCurrentInterval: (T) -> Int,
        currentTime: Long = System.currentTimeMillis(),
    ): List<T> {
        return items.filter { item ->
            val result = calculate(
                chapters = getChapters(item),
                currentFetchInterval = getCurrentInterval(item),
                lastFetchTimestamp = getLastFetch(item),
                currentTime = currentTime,
            )
            result.isDueForUpdate
        }
    }
}
