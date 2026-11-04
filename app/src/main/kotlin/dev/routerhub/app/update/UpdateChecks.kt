package dev.routerhub.app.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.routerhub.app.AppContainer
import dev.routerhub.app.BuildConfig
import dev.routerhub.app.R
import dev.routerhub.app.RouterHubApp
import dev.routerhub.app.extension.ExtensionStore
import dev.routerhub.app.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Mihon's automatic update checks, for builds that update themselves (not the Play build, where Play
 * updates the app and the extensions are built in): about twice a day, look for a new RouterHub and for
 * extension updates in the repositories, and announce each once.
 */
object UpdateChecks {
    private const val JOB_ID = 1002
    private const val CHANNEL = "updates"
    private const val APP_NOTIFICATION = 2002
    private const val EXTENSIONS_NOTIFICATION = 2003

    /** Intent extra saying what a notification opens. */
    const val EXTRA_OPEN = "dev.routerhub.app.OPEN"
    const val OPEN_EXTENSIONS = "extensions"
    const val OPEN_APP_UPDATE = "app_update"

    val available: Boolean get() = BuildConfig.UPDATE_CHECKER || BuildConfig.INSTALLABLE_EXTENSIONS

    /** Schedules the check while automatic updates are on, and cancels it when they're off. */
    fun sync(context: Context, enabled: Boolean) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!available || !enabled) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJob::class.java))
                .setPeriodic(TimeUnit.HOURS.toMillis(12))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build(),
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Creates the channel the first time and returns its id. */
    fun channel(context: Context): String {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.updates_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.updates_channel_summary)
            },
        )
        return CHANNEL
    }

    suspend fun check(container: AppContainer) {
        if (BuildConfig.INSTALLABLE_EXTENSIONS) checkExtensions(container)
        if (BuildConfig.UPDATE_CHECKER) checkApp(container)
    }

    private suspend fun checkExtensions(container: AppContainer) {
        // Extensions load in the background after the process starts.
        withTimeoutOrNull(30.seconds) { container.extensions.ready.first { it } } ?: return
        container.store.refreshNow()
        val updates = ExtensionStore.updates(container.extensions.extensions.value, container.store.available.value)
        val key = updates.map { "${it.second.packageName}:${it.second.versionCode}" }.sorted().joinToString(",")
        if (updates.isEmpty() || key == container.preferences.notifiedExtensionUpdates) return
        val context = container.context
        val names = updates.joinToString(", ") { it.second.name }
        notify(
            context,
            EXTENSIONS_NOTIFICATION,
            NotificationCompat.Builder(context, channel(context))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.resources.getQuantityString(R.plurals.extension_updates_title, updates.size, updates.size))
                .setContentText(names)
                .setContentIntent(open(context, OPEN_EXTENSIONS))
                .setAutoCancel(true)
                .build(),
        )
        container.preferences.notifiedExtensionUpdates = key
    }

    private suspend fun checkApp(container: AppContainer) {
        val result = try {
            AppUpdateChecker().check()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        container.preferences.lastAppUpdateCheck = System.currentTimeMillis()
        if (result !is UpdateResult.NewUpdate || result.release.version == container.preferences.notifiedAppVersion) return
        val context = container.context
        notify(
            context,
            APP_NOTIFICATION,
            NotificationCompat.Builder(context, channel(context))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_update_title, result.release.version))
                .setContentText(context.getString(R.string.app_update_text))
                .setContentIntent(open(context, OPEN_APP_UPDATE))
                .setAutoCancel(true)
                .build(),
        )
        container.preferences.notifiedAppVersion = result.release.version
    }

    private fun open(context: Context, what: String) = PendingIntent.getActivity(
        context,
        what.hashCode(),
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, what)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notify(context: Context, id: Int, notification: Notification) {
        if (!canNotify(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications were switched off between the check and now.
        }
    }
}

/** Android runs this about every 12 hours while automatic update checks are on. */
class UpdateJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val container = (application as RouterHubApp).container
        running = scope.launch {
            try {
                UpdateChecks.check(container)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
