package dev.routerhub.app.extension

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.util.Log
import dev.routerhub.api.ExtensionContract
import dev.routerhub.api.RouterExtension
import java.io.File

/**
 * Turns an installed extension package into live [dev.routerhub.api.RouterFirmwareFactory]
 * instances.
 *
 * Order matters: every check that can reject a package (metadata, API version, signature) runs on
 * PackageManager data only. Extension bytecode is touched only after all of them have passed.
 */
class ExtensionLoader(
    private val context: Context,
    private val verifier: SignatureVerifier,
) {

    fun load(packageInfo: PackageInfo): ExtensionState {
        val pkg = packageInfo.packageName
        val appInfo = packageInfo.applicationInfo
        val verdict = verifier.verify(packageInfo)
        val apiVersion = appInfo?.metaData?.getInt(ExtensionContract.META_API_VERSION, -1) ?: -1

        val info = ExtensionInfo(
            packageName = pkg,
            label = appInfo?.let { resolveLabel(it) } ?: pkg,
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = packageInfo.longVersionCode,
            apiVersion = apiVersion,
            signerSha256 = verdict.signerSha256,
        )

        // 1. Compatibility. Rejects extensions built against a router-api we can't link.
        if (appInfo == null) return ExtensionState.Incompatible(info, "Package has no application info")
        if (apiVersion !in ExtensionContract.MIN_SUPPORTED_API_VERSION..ExtensionContract.API_VERSION) {
            return ExtensionState.Incompatible(
                info,
                "Built for router-api v$apiVersion; this app supports " +
                    "v${ExtensionContract.MIN_SUPPORTED_API_VERSION}..v${ExtensionContract.API_VERSION}",
            )
        }

        // 2. Authenticity. Nothing below this line may run for an untrusted package.
        val trust = when (verdict) {
            is SignatureVerdict.Trusted -> verdict.level
            is SignatureVerdict.Untrusted -> return ExtensionState.Untrusted(info)
        }

        val entryClasses = appInfo.metaData
            ?.getString(ExtensionContract.META_ENTRY_CLASS).orEmpty()
            .split(';')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { if (it.startsWith('.')) pkg + it else it }
        if (entryClasses.isEmpty()) {
            return ExtensionState.Incompatible(info, "Missing ${ExtensionContract.META_ENTRY_CLASS} meta-data")
        }

        // 3. Code. Any failure from here on is the extension's fault, not the user's.
        return try {
            // A *resources-only* Context for the extension package. Note the flags: 0, NOT
            // CONTEXT_INCLUDE_CODE. With INCLUDE_CODE, Android would create a class loader for the
            // APK whose parent is the boot class loader, not ours. Its RouterFirmware would then
            // be a different Class object from the host's, and every cast would fail with a
            // ClassCastException. We want its resources (label, icon, assets) from here; its code
            // is loaded by our own ExtensionClassLoader below.
            val resources = context.createPackageContext(pkg, 0)

            val classLoader = ExtensionClassLoader(
                dexPath = dexPathOf(appInfo),
                librarySearchPath = appInfo.nativeLibraryDir,
                host = context.classLoader, // the loader that defined router-api in this process
            )

            val factories = entryClasses.flatMap { className ->
                // initialize = false: don't run the class's static initialisers until we know it
                // is actually a RouterExtension.
                val clazz = Class.forName(className, false, classLoader)
                require(RouterExtension::class.java.isAssignableFrom(clazz)) {
                    "$className does not implement ${RouterExtension::class.java.name}"
                }
                val extension = clazz.getDeclaredConstructor().newInstance() as RouterExtension
                extension.factories
            }

            ExtensionState.Loaded(info, trust, factories, resources)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load extension $pkg", e)
            ExtensionState.Failed(info, e)
        } catch (e: LinkageError) {
            // NoClassDefFoundError, AbstractMethodError, ExceptionInInitializerError...: the
            // extension references host classes or members that don't exist in this host build.
            Log.e(TAG, "Extension $pkg is binary-incompatible with this host", e)
            ExtensionState.Failed(info, e)
        }
    }

    /**
     * The installed APK plus any split APKs, joined with ':'.
     *
     * These live under /data/app/~~<random>/ and are read-only, which Android 14+ requires: ART
     * refuses to load dex files the loading app can write ("safer dynamic code loading"). So never
     * copy an extension APK into our own storage to load it from there.
     *
     * Updates are installed into a NEW random directory. The path we verified therefore refers to
     * exactly the bytes whose signature we checked; an update racing with this load makes loading
     * fail rather than run unverified code.
     */
    private fun dexPathOf(appInfo: ApplicationInfo): String =
        (listOf(appInfo.sourceDir) + appInfo.splitSourceDirs.orEmpty()).joinToString(File.pathSeparator)

    private fun resolveLabel(appInfo: ApplicationInfo): String =
        appInfo.loadLabel(context.packageManager).toString()

    private companion object {
        const val TAG = "ExtensionLoader"
    }
}
