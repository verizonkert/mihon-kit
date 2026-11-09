package eu.kanade.tachiyomi.core.security

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Manages user privacy and telemetry preferences such as Crashlytics and anonymous analytics.
 *
 * @param preferenceStore The key-value [PreferenceStore] where privacy settings are persisted.
 */
@Inject
@SingleIn(AppScope::class)
class PrivacyPreferences(
    preferenceStore: PreferenceStore,
) {
    /**
     * Whether automatic crash and error reporting to Firebase Crashlytics is enabled.
     */
    val crashlytics: Preference<Boolean> = preferenceStore.getBoolean("crashlytics", true)

    /**
     * Whether anonymous usage telemetry and analytics collection is permitted.
     */
    val analytics: Preference<Boolean> = preferenceStore.getBoolean("analytics", true)
}
