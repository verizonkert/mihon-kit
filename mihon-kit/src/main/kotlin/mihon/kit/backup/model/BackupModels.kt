package mihon.kit.backup.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Root Tachiyomi / Mihon `.tachibk` backup model.
 * Matches Tachiyomi / Mihon protobuf schema with exact field `@ProtoNumber` mappings.
 *
 * @property backupManga List of backed up manga entries with chapter and history data.
 * @property backupCategories Custom category folders defined in the library.
 * @property backupSources List of sources used by the library.
 * @property backupPreferences Backed up user preferences and settings.
 * @property backupExtensionRepos External extension repository definitions.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(100) val backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(101) val backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(102) val backupExtensionRepos: List<BackupExtensionRepo> = emptyList(),
)

/**
 * Serialized representation of a single manga entry in a backup file.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupManga(
    @ProtoNumber(1) val source: Long = 0L,
    @ProtoNumber(2) val url: String = "",
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val artist: String? = null,
    @ProtoNumber(5) val author: String? = null,
    @ProtoNumber(6) val description: String? = null,
    @ProtoNumber(7) val genre: List<String> = emptyList(),
    @ProtoNumber(8) val status: Int = 0,
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(13) val customTitle: String? = null,
    @ProtoNumber(14) val customArtist: String? = null,
    @ProtoNumber(15) val customAuthor: String? = null,
    @ProtoNumber(16) val customDescription: String? = null,
    @ProtoNumber(17) val customGenre: List<String> = emptyList(),
    @ProtoNumber(18) val customStatus: Int = 0,
    @ProtoNumber(100) val comments: Boolean = false,
    @ProtoNumber(101) val chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(102) val categories: List<Long> = emptyList(),
    @ProtoNumber(103) val tracking: List<BackupTracking> = emptyList(),
    @ProtoNumber(104) val favorite: Boolean = true,
    @ProtoNumber(105) val chapterFlags: Int = 0,
    @ProtoNumber(107) val viewer: Int = 0,
    @ProtoNumber(108) val history: List<BackupHistory> = emptyList(),
    @ProtoNumber(109) val updateStrategy: Int = 0,
    @ProtoNumber(110) val lastModifiedAt: Long = 0L,
    @ProtoNumber(111) val favoriteModifiedAt: Long? = null,
    @ProtoNumber(112) val excludedScanlators: List<String> = emptyList(),
    @ProtoNumber(113) val version: Long = 0L,
)

/**
 * Serialized representation of a single chapter entry in a backup file.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupChapter(
    @ProtoNumber(1) val url: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val scanlator: String? = null,
    @ProtoNumber(4) val read: Boolean = false,
    @ProtoNumber(5) val bookmark: Boolean = false,
    @ProtoNumber(6) val lastPageRead: Long = 0L,
    @ProtoNumber(7) val dateFetch: Long = 0L,
    @ProtoNumber(8) val dateUpload: Long = 0L,
    @ProtoNumber(9) val chapterNumber: Float = 0f,
    @ProtoNumber(10) val sourceOrder: Long = 0L,
    @ProtoNumber(11) val lastModifiedAt: Long = 0L,
    @ProtoNumber(12) val version: Long = 0L,
)

/**
 * Serialized representation of a custom library category.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupCategory(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val order: Long = 0L,
    @ProtoNumber(3) val id: Long = 0L,
    @ProtoNumber(100) val flags: Long = 0L,
)

/**
 * Serialized representation of a source used in the backed up library.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long = 0L,
)

/**
 * Serialized representation of a reading history entry.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupHistory(
    @ProtoNumber(1) val url: String = "",
    @ProtoNumber(2) val lastRead: Long = 0L,
    @ProtoNumber(3) val readDuration: Long = 0L,
)

/**
 * Serialized representation of an external tracker entry (e.g. AniList, MyAnimeList, Kitsu, MangaUpdates).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupTracking(
    @ProtoNumber(1) val syncId: Int = 0,
    @ProtoNumber(2) val libraryId: Long = 0L,
    @ProtoNumber(3) val mediaIdInt: Int = 0,
    @ProtoNumber(4) val trackingUrl: String = "",
    @ProtoNumber(5) val title: String = "",
    @ProtoNumber(6) val lastChapterRead: Float = 0f,
    @ProtoNumber(7) val totalChapters: Int = 0,
    @ProtoNumber(8) val score: Float = 0f,
    @ProtoNumber(9) val status: Int = 0,
    @ProtoNumber(10) val startedReadingDate: Long = 0L,
    @ProtoNumber(11) val finishedReadingDate: Long = 0L,
    @ProtoNumber(100) val mediaId: Long = 0L,
)

/**
 * Serialized representation of an external extension repository.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupExtensionRepo(
    @ProtoNumber(1) val baseUrl: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val shortName: String? = null,
    @ProtoNumber(4) val website: String = "",
    @ProtoNumber(5) val signingKeyFingerprint: String = "",
)

/**
 * Serialized representation of a backed up user preference key-value pair.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupPreference(
    @ProtoNumber(1) val key: String = "",
    @ProtoNumber(2) val value: PreferenceValue,
)

/**
 * Polymorphic protobuf value container for backed up preference data.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
sealed interface PreferenceValue {
    @Serializable
    data class BooleanPreferenceValue(@ProtoNumber(1) val value: Boolean) : PreferenceValue

    @Serializable
    data class FloatPreferenceValue(@ProtoNumber(1) val value: Float) : PreferenceValue

    @Serializable
    data class IntPreferenceValue(@ProtoNumber(1) val value: Int) : PreferenceValue

    @Serializable
    data class LongPreferenceValue(@ProtoNumber(1) val value: Long) : PreferenceValue

    @Serializable
    data class StringPreferenceValue(@ProtoNumber(1) val value: String) : PreferenceValue

    @Serializable
    data class StringSetPreferenceValue(@ProtoNumber(1) val value: Set<String>) : PreferenceValue
}
