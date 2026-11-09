import mihon.gradle.extensions.alias
import mihon.gradle.extensions.libs
import mihon.gradle.extensions.mihonx
import mihon.gradle.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin for Android test modules (e.g. macrobenchmark and UI test suites).
 * Applies Android test plugin and base project configuration.
 */
@Suppress("UNUSED")
class PluginAndroidTest : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.android.test)
            alias(mihonx.plugins.android.base)
        }
    }
}
