import mihon.gradle.extensions.alias
import mihon.gradle.extensions.libs
import mihon.gradle.extensions.mihonx
import mihon.gradle.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin for Android library modules.
 * Applies the Android library plugin and standard project configurations (`PluginAndroidBase`).
 */
@Suppress("UNUSED")
class PluginAndroidLibrary : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.android.library)
            alias(mihonx.plugins.android.base)
        }
    }
}
