package mihon.core.metro

import dev.zacsweers.metro.Qualifier

/**
 * Metro qualifier indicating whether the currently executing app is a debug build (`BuildConfig.DEBUG`).
 */
@Qualifier
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class IsDebugBuild

