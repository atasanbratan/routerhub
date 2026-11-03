package dev.routerhub.app.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.routerhub.api.Capability
import dev.routerhub.api.ConnectedClient
import dev.routerhub.app.AppContainer
import dev.routerhub.app.R
import dev.routerhub.app.data.DeviceBook
import dev.routerhub.app.data.RouterProfile
import dev.routerhub.app.data.toConnectionConfig
import dev.routerhub.app.engine.FirmwareSession
import dev.routerhub.app.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Background checks for devices this phone has never seen on a router. Each router opts in, the check
 * runs about every 15 minutes (Android's shortest period), only on an unmetered network, and only for
 * routers on the network the phone is on.
 */
object NewDeviceAlerts {
    private const val JOB_ID = 1001
    private const val CHANNEL = "new_devices"
    private const val TAG = "NewDeviceAlerts"

    /** Intent extra naming the router a notification opens. */
    const val EXTRA_ROUTER_ID = "dev.routerhub.app.ROUTER_ID"

    /** Runs the periodic job while any router wants alerts, and cancels it once none does. */
    fun sync(context: Context, profiles: List<RouterProfile>) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (profiles.none { it.alertNewDevices }) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, NewDeviceJob::class.java))
            .setPeriodic(TimeUnit.MINUTES.toMillis(15))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }

    /** False while Android 13+ hasn't been allowed to show this app's notifications. */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** One round: every opted-in router on this network is asked for its clients. */
    suspend fun check(container: AppContainer) {
        val context = container.context
        val profiles = container.routers.profiles.value.filter { it.alertNewDevices && !it.passwordNeeded }
        if (profiles.isEmpty()) return
        // Extensions load in the background after the process starts.
        withTimeoutOrNull(30.seconds) { container.extensions.ready.first { it } } ?: return
        val factories = container.extensions.firmwares.first()
        for (profile in profiles) {
            val factory = factories[profile.firmwareId] ?: continue
            if (Capability.CONNECTED_CLIENTS !in factory.capabilities) continue
            if (!HomeNetwork.isOn(context, profile.host)) continue
            // Another network can use the same address: only a router that looks like this one gets its login.
            val identity = RouterIdentity.of(profile)
            if (identity == null || identity != profile.identity) {
                Log.i(TAG, "Skipped ${profile.name}: it doesn't look like the router seen in the app")
                continue
            }
            try {
                val clients = FirmwareSession.open(factory, profile.toConnectionConfig()).let { session ->
                    try {
                        session.connect()
                        session.connectedClients()
                    } finally {
                        session.close()
                    }
                }
                // Read again: the user may have opened the router while this one was logging in.
                val devices = container.routers.get(profile.id)?.devices ?: continue
                val found = DeviceBook.newDevices(devices, clients)
                container.routers.updateDevices(profile.id) { DeviceBook.record(it, clients, System.currentTimeMillis()) }
                if (found.isNotEmpty()) notify(context, profile, found)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The router is off, busy or refusing the login; the next round tries again.
                Log.i(TAG, "Check of ${profile.name} failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun notify(context: Context, profile: RouterProfile, found: List<ConnectedClient>) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.alerts_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.alerts_channel_summary)
            },
        )
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_ROUTER_ID, profile.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val tap = PendingIntent.getActivity(
            context,
            profile.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val names = found.map { it.hostname ?: it.macAddress }
        val title = context.resources.getQuantityString(R.plurals.alerts_title, found.size, found.size, profile.name)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(names.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(names.joinToString("\n")))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            // One notification per router, replaced by the next finding.
            NotificationManagerCompat.from(context).notify(profile.id, JOB_ID, notification)
        } catch (_: SecurityException) {
            // Notifications were switched off between the check and now.
        }
    }
}
