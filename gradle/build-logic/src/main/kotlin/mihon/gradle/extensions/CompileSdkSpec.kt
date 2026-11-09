package mihon.gradle.extensions

import com.android.build.api.dsl.CompileSdkSpec
import com.android.build.api.dsl.CompileSdkVersion
import org.gradle.api.provider.Provider

/**
 * Parses and configures the compile SDK version with optional minor API level
 * from a version string [Provider] (e.g. "35" or "35.1").
 */
internal fun CompileSdkSpec.release(version: Provider<String>): CompileSdkVersion {
    val (major, minor) = version.get().split(".", limit = 2).let {
        it[0].toInt() to it.getOrNull(1)?.toInt()
    }
    return release(major) { minorApiLevel = minor }
}

