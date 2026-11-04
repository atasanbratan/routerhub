package dev.routerhub.app

import android.app.Application
import dev.routerhub.app.alerts.NewDeviceAlerts
import dev.routerhub.app.update.UpdateChecks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RouterHubApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
        // Schedules or cancels the new-device check whenever a router's alert setting changes.
        appScope.launch {
            container.routers.profiles
                .map { profiles -> profiles.filter { it.alertNewDevices }.map { it.id } }
                .distinctUntilChanged()
                .collect { NewDeviceAlerts.sync(this@RouterHubApp, container.routers.profiles.value) }
        }
        appScope.launch {
            container.preferences.autoUpdates.collect { UpdateChecks.sync(this@RouterHubApp, it) }
        }
    }
}
