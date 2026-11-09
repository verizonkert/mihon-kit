package mihon.kit.loader

import android.os.Build
import dalvik.system.DelegateLastClassLoader
import dalvik.system.PathClassLoader
import java.net.URL
import java.util.Collections
import java.util.Enumeration

/**
 * Creates a specialized [ClassLoader] for an isolated extension APK, prioritizing the extension's
 * DEX bytecode over classes in the host application while delegating system/boot classes first.
 *
 * This prevents dependency and class collisions when an extension bundles newer or modified versions
 * of third-party libraries (e.g. OkHttp, JSoup, Kotlin stdlib) that already exist in the host application.
 *
 * - On Android 8.0 (API 26) and above, it uses the platform-provided [DelegateLastClassLoader].
 * - On pre-Android 8.0 devices, it falls back to [DelegateLastPathClassLoader], which mirrors the
 *   delegate-last delegation order.
 *
 * @param dexPath The absolute filesystem path to the extension's `.apk` or `.dex` file.
 * @param librarySearchPath Optional path to directory containing native `.so` shared libraries, or null.
 * @param parent The parent class loader, typically the host application's class loader.
 * @return A [ClassLoader] configured to load the extension's classes.
 */
fun createExtensionClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
): ClassLoader {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        DelegateLastClassLoader(dexPath, librarySearchPath, parent)
    } else {
        DelegateLastPathClassLoader(dexPath, librarySearchPath, parent)
    }
}

/**
 * A custom [PathClassLoader] fallback implementation for Android 7.1 and below (API < 26)
 * that provides "parent-last" / "delegate-last" classloading semantics.
 *
 * Lookup order:
 * 1. Already-loaded classes via [findLoadedClass].
 * 2. Boot/System class loader (Android framework runtime classes).
 * 3. The extension's own DEX path via [findClass].
 * 4. The parent application's class loader as a final fallback.
 */
private class DelegateLastPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    private val bootClassLoader: ClassLoader? = Any::class.java.classLoader

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        findLoadedClass(name)?.let { return it }

        if (bootClassLoader != null) {
            try {
                return bootClassLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {}
        }

        val fromSuper = try {
            return findClass(name)
        } catch (e: ClassNotFoundException) {
            e
        }

        return try {
            parent.loadClass(name)
        } catch (_: ClassNotFoundException) {
            throw fromSuper
        }
    }

    override fun getResource(name: String?): URL? {
        return bootClassLoader?.getResource(name)
            ?: findResource(name)
            ?: parent?.getResource(name)
    }

    override fun getResources(name: String?): Enumeration<URL> {
        val resources = buildList {
            bootClassLoader?.getResources(name)?.let { addAll(it.toList()) }
            findResources(name)?.let { addAll(it.toList()) }
            parent?.getResources(name)?.let { addAll(it.toList()) }
        }
        return Collections.enumeration(resources)
    }
}
