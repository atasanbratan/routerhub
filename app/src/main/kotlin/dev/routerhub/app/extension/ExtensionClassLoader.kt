package dev.routerhub.app.extension

import dalvik.system.PathClassLoader

/**
 * Class loader for one extension APK, with *selective* delegation.
 *
 * Why not a plain DexClassLoader/PathClassLoader with parent-first delegation? On the JVM and ART, a
 * class's identity is the pair (name, defining loader). For `extension as RouterFirmware` to work,
 * `dev.routerhub.api.RouterFirmware` must be defined by the HOST loader on both sides of the cast,
 * and so must every type in its signatures: the models, the exceptions, and also
 * `kotlin.coroutines.Continuation`, which the Kotlin compiler adds to every suspend function.
 *
 * So we split the world in two:
 *  - Shared packages ([SHARED_PACKAGES]) always resolve through the host. Even if an extension
 *    accidentally bundles its own router-api or stdlib, those copies are never used, so
 *    ClassCastException / "loader constraint violation" can't happen.
 *  - Everything else resolves child-first: the extension's own bundled libraries (Jsoup, JSch...)
 *    win over whatever version the host happens to contain, so a host dependency bump can't break
 *    an extension that ships a different version of the same library.
 *
 * On API 26+ DexClassLoader is just a PathClassLoader whose optimizedDirectory argument is ignored,
 * so we extend PathClassLoader directly. On Android 14+, every path in [dexPath] must be read-only
 * (see ExtensionLoader).
 */
internal class ExtensionClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    private val host: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, host) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(this) {
            // 1. Already defined by this loader? (Classes from the extension's own dex.)
            findLoadedClass(name)?.let { return it }

            // 2. Platform and shared contract types: always the host's definition.
            if (SHARED_PACKAGES.any { name.startsWith(it) }) return host.loadClass(name)

            // 3. Child-first: look in the extension's dex, fall back to the host for anything else.
            return try {
                findClass(name)
            } catch (_: ClassNotFoundException) {
                host.loadClass(name)
            }
        }
    }

    companion object {
        /**
         * The "platform" the host provides. Extensions must compile against these as compileOnly,
         * and the host must keep them un-obfuscated (see proguard-rules.pro). Removing or
         * downgrading one of the libraries here is a breaking change: bump
         * ExtensionContract.API_VERSION.
         */
        val SHARED_PACKAGES = listOf(
            "java.", "javax.", "android.", "dalvik.", "org.json.", "org.w3c.", "org.xml.",
            "kotlin.",
            "kotlinx.coroutines.",
            "okhttp3.", "okio.",
            "dev.routerhub.api.",
        )
    }
}
