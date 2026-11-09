package eu.kanade.tachiyomi.network

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import mihon.core.metro.IsDebugBuild
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Preferences for configuring OkHttp networking behavior, User-Agent header rotation,
 * DNS over HTTPS (DoH) providers, and logging.
 *
 * @param preferenceStore The key-value store where preferences are saved.
 * @param isDebugBuild Indicates whether the app was compiled in debug mode (enables verbose logs by default).
 */
@Inject
@SingleIn(AppScope::class)
class NetworkPreferences(
    preferenceStore: PreferenceStore,
    @IsDebugBuild isDebugBuild: Boolean,
) {

    /**
     * Whether sensitive HTTP authentication headers (Authorization, Cookie) are masked in logs.
     */
    val redactSensitiveHeaders: Preference<Boolean> = preferenceStore.getBoolean(
        "redact_sensitive_headers",
        true,
    )

    /**
     * Controls OkHttp logging interceptor verbosity (NONE vs BODY).
     */
    val verboseLogging: Preference<Boolean> = preferenceStore.getBoolean(
        "verbose_logging",
        isDebugBuild,
    )

    /**
     * Active DNS over HTTPS (DoH) provider index (e.g. [PREF_DOH_CLOUDFLARE], [PREF_DOH_GOOGLE]),
     * or -1 if DoH is disabled.
     */
    val dohProvider: Preference<Int> = preferenceStore.getInt("doh_provider", -1)

    /**
     * Default browser User-Agent string sent with extension requests to avoid bot blocks.
     */
    val defaultUserAgent: Preference<String> = preferenceStore.getString(
        "default_user_agent",
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36",
    )
}
