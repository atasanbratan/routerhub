package dev.routerhub.app.alerts

import android.app.job.JobParameters
import android.app.job.JobService
import dev.routerhub.app.RouterHubApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Android runs this about every 15 minutes while a router wants new-device alerts. */
class NewDeviceJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val container = (application as RouterHubApp).container
        running = scope.launch {
            try {
                NewDeviceAlerts.check(container)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    /** Android stopped the job early (the network went away, say); the next period tries again. */
    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
