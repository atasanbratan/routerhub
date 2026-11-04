package dev.routerhub.app.update

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import dev.routerhub.app.R
import dev.routerhub.app.extension.SignatureVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Mihon's in-app update download: fetches the new APK with a progress notification, then hands it to
 * the system installer. The APK must be this app, signed with this app's key, so a tampered or
 * mixed-up download is never offered for install (Android would refuse it as an update anyway).
 */
class AppUpdateDownloader(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    /** Downloads [release]; one that carries no APK opens its page instead. */
    fun download(release: AppRelease) {
        if (!release.downloadUrl.endsWith(".apk")) {
            openPage(release.releaseUrl)
            return
        }
        if (_downloading.value) return
        _downloading.value = true
        scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) { fetch(release) }
                notify(done(release, apk))
                ApkInstaller.install(context, apk)
            } catch (e: UpdateException) {
                notify(failed(release, e.message.orEmpty()))
                withContext(Dispatchers.Main) { Toast.makeText(context, e.message, Toast.LENGTH_LONG).show() }
            } finally {
                _downloading.value = false
            }
        }
    }

    private fun fetch(release: AppRelease): File {
        val dir = File(context.cacheDir, APK_DIR).apply { mkdirs() }
        val file = File(dir, "routerhub.apk")
        try {
            client.newCall(Request.Builder().url(release.downloadUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("empty answer")
                val total = body.contentLength()
                var read = 0L
                var shown = -1
                file.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            read += count
                            val percent = if (total > 0) (read * 100 / total).toInt() else -1
                            if (percent != shown) {
                                shown = percent
                                notify(progress(release, percent))
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            file.delete()
            throw UpdateException(context.getString(R.string.update_download_failed))
        }
        if (!isThisApp(file)) {
            file.delete()
            throw UpdateException(context.getString(R.string.update_wrong_signature))
        }
        return file
    }

    private fun isThisApp(file: File): Boolean {
        val info = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return false
        val signers = SignatureVerifier.currentSigners(info).map { SignatureVerifier.sha256Hex(it.toByteArray()) }
        return info.packageName == context.packageName && signers.isNotEmpty() && signers.toSet() == SignatureVerifier.hostSigners(context).toSet()
    }

    private fun openPage(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun builder(release: AppRelease) = NotificationCompat.Builder(context, UpdateChecks.channel(context))
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.update_downloading, release.version))

    private fun progress(release: AppRelease, percent: Int) = builder(release)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, percent.coerceAtLeast(0), percent < 0)
        .build()

    private fun done(release: AppRelease, apk: File) = builder(release)
        .setContentTitle(context.getString(R.string.update_downloaded))
        .setContentText(context.getString(R.string.update_downloaded_text, release.version))
        .setContentIntent(PendingIntent.getActivity(context, 0, ApkInstaller.intent(context, apk), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .setAutoCancel(true)
        .build()

    /** Says why, since a failed download and a refused one need different things from the user. */
    private fun failed(release: AppRelease, reason: String) = builder(release)
        .setContentTitle(reason)
        .setContentText(context.getString(R.string.update_download_failed_text))
        .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(reason).bigText(context.getString(R.string.update_download_failed_text)))
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(Intent.ACTION_VIEW, release.releaseUrl.toUri()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .setAutoCancel(true)
        .build()

    private fun notify(notification: Notification) {
        if (!UpdateChecks.canNotify(context)) return
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notifications were switched off a moment ago.
        }
    }

    private class UpdateException(message: String) : Exception(message)

    private companion object {
        const val APK_DIR = "updates"
        const val NOTIFICATION_ID = 2001
    }
}
