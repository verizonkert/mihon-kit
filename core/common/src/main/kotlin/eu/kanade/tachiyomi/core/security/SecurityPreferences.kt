package eu.kanade.tachiyomi.core.security

import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.MR

/**
 * Manages security and authentication preferences including biometric app lock,
 * screen protection flags, and notification privacy.
 *
 * @param preferenceStore The key-value [PreferenceStore] where security settings are persisted.
 */
@Inject
@SingleIn(AppScope::class)
class SecurityPreferences(
    preferenceStore: PreferenceStore,
) {

    /**
     * Whether biometric/device credentials authentication is required to unlock the application.
     */
    val useAuthenticator: Preference<Boolean> = preferenceStore.getBoolean("use_biometric_lock", false)

    /**
     * Inactivity duration in minutes before the app locks itself after being placed in background.
     * 0 indicates immediate locking.
     */
    val lockAppAfter: Preference<Int> = preferenceStore.getInt("lock_app_after", 0)

    /**
     * Controls when `FLAG_SECURE` is applied to Android windows to prevent screenshots and preview in recent apps.
     */
    val secureScreen: Preference<SecureScreenMode> = preferenceStore.getEnum(
        "secure_screen_v2",
        SecureScreenMode.INCOGNITO,
    )

    /**
     * Whether sensitive chapter or manga details are masked from notifications.
     */
    val hideNotificationContent: Preference<Boolean> = preferenceStore.getBoolean("hide_notification_content", false)

    /**
     * For app lock. Will be set when there is a pending timed lock.
     * Otherwise, this pref should be deleted.
     */
    val lastAppClosed: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("last_app_closed"),
        0,
    )

    /**
     * Defines window security modes for preventing screen recording or recent app previews.
     *
     * @property titleRes String resource containing the human-readable display label.
     */
    enum class SecureScreenMode(val titleRes: StringResource) {
        /** Window protection is always active across all screens. */
        ALWAYS(MR.strings.lock_always),

        /** Window protection is active only when incognito mode is turned on. */
        INCOGNITO(MR.strings.pref_incognito_mode),

        /** Window protection is disabled. */
        NEVER(MR.strings.lock_never),
    }
}
