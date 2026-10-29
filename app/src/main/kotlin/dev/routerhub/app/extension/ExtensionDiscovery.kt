package dev.routerhub.app.extension

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dev.routerhub.api.ExtensionContract

/**
 * Finds installed extension APKs. Only reads PackageManager metadata; never touches their code.
 *
 * An extension is recognised by `<uses-feature android:name="dev.routerhub.extension" />` in its
 * manifest. That only marks a package as a *candidate*. Trust is decided later by
 * [SignatureVerifier], since any app can declare the feature.
 */
class ExtensionDiscovery(private val packageManager: PackageManager) {

    fun findInstalled(): List<PackageInfo> =
        installedPackages().filter(::isExtension)

    private fun installedPackages(): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(FLAGS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(FLAGS)
        }

    private fun isExtension(info: PackageInfo): Boolean =
        info.reqFeatures.orEmpty().any { it.name == ExtensionContract.FEATURE }

    private companion object {
        const val FLAGS =
            PackageManager.GET_CONFIGURATIONS or // populates reqFeatures (<uses-feature>)
                PackageManager.GET_META_DATA or // populates applicationInfo.metaData
                PackageManager.GET_SIGNING_CERTIFICATES // populates signingInfo
    }
}
