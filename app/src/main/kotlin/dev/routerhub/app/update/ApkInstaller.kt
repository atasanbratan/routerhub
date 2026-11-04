package dev.routerhub.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded APK to the system installer, which asks the user. Only builds that can install
 * extensions declare the FileProvider (and REQUEST_INSTALL_PACKAGES) this needs; the APK must be in one
 * of the cache folders res/xml/extension_apks.xml shares.
 */
object ApkInstaller {
    fun intent(context: Context, apk: File): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.extensions", apk), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    /** False when no app on the phone installs packages. */
    fun install(context: Context, apk: File): Boolean = try {
        context.startActivity(intent(context, apk))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
