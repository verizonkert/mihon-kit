package mihon.gradle.extensions

import com.android.build.api.dsl.CommonExtension
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.accessors.dm.LibrariesForMihonx
import org.gradle.api.Project
import org.gradle.api.plugins.PluginManager
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType

/** Accessor for the standard `libs` version catalog. */
internal val Project.libs get() = the<LibrariesForLibs>()

/** Accessor for the `mihonx` internal project version catalog. */
internal val Project.mihonx get() = the<LibrariesForMihonx>()

/** Configures plugins using the project's [PluginManager]. */
internal fun Project.plugins(block: PluginManager.() -> Unit) {
    pluginManager.apply(block)
}

/** Configures common Android extension settings. */
internal fun Project.android(block: CommonExtension.() -> Unit) {
    extensions.configure(block)
}

/**
 * Configures all [Test] tasks across the project to run on JUnit Platform
 * with standardized passed, skipped, and failed test logging.
 */
fun Project.configureTest() {
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }
}
