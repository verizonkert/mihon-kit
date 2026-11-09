package mihon.gradle

import org.gradle.api.Project

/**
 * Build configuration flags parsed from Gradle properties and distribution targets.
 */
interface BuildConfig {
    /** Whether telemetry and crash reporting components should be enabled. */
    val includeTelemetry: Boolean

    /** Whether Proguard/R8 mapping files should be uploaded to Firebase Crashlytics. */
    val uploadCrashlyticsMapping: Boolean

    /** Whether in-app self-updater checks are active. */
    val enableUpdater: Boolean

    /** Whether dependencies info block should be included in generated APK metadata. */
    val includeDependencyInfo: Boolean
}

private enum class Distribution {
    LOCAL,
    CI,
    GITHUB,
    FOSS,
}

/**
 * Accessor for project [BuildConfig] values determined by `-Pdist` and command-line flags.
 */
val Project.Config: BuildConfig get() = object : BuildConfig {
    private val distribution: Distribution = project.providers.gradleProperty("dist").orNull
        ?.let { name ->
            Distribution.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: error("Unknown -Pdist=$name, expected one of $distributionNames")
        }
        ?: Distribution.LOCAL

    override val includeTelemetry: Boolean = project.flag("include-telemetry")
        ?: (distribution == Distribution.CI || distribution == Distribution.GITHUB)

    override val uploadCrashlyticsMapping: Boolean = includeTelemetry && (distribution == Distribution.GITHUB)

    override val enableUpdater: Boolean = project.flag("enable-updater")
        ?: (distribution != Distribution.LOCAL)

    override val includeDependencyInfo: Boolean = project.flag("include-dependency-info") ?: false
}

private val distributionNames = Distribution.entries.joinToString { it.name.lowercase() }

private fun Project.flag(name: String): Boolean? = providers.gradleProperty(name).orNull
    ?.let { it.isEmpty() || it.toBoolean() }
