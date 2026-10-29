package dev.routerhub.app.engine

import dev.routerhub.api.ConnectionConfig
import dev.routerhub.api.RouterFirmwareFactory
import dev.routerhub.app.data.RouterProfile
import dev.routerhub.app.data.toConnectionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps at most one connected [FirmwareSession] per saved router, so screens don't log in again
 * on every refresh.
 *
 * A cached session is reused only while both the profile's connection settings and the factory
 * *instance* are unchanged. A new factory instance means the extension was reloaded (updated or
 * reinstalled), and the old session would keep running the old code.
 */
class SessionManager(private val scope: CoroutineScope) {

    private class Entry(val factory: RouterFirmwareFactory, val config: ConnectionConfig, val session: FirmwareSession)

    private val entries = mutableMapOf<String, Entry>()
    private val mutex = Mutex()

    /** Returns a logged-in session, connecting first if needed. Failed connections aren't cached. */
    suspend fun obtain(profile: RouterProfile, factory: RouterFirmwareFactory): FirmwareSession = mutex.withLock {
        val config = profile.toConnectionConfig()
        entries[profile.id]?.let { entry ->
            if (entry.factory === factory && entry.config == config) return entry.session
            entries.remove(profile.id)
            scope.launch { entry.session.close() }
        }
        val session = FirmwareSession.open(factory, config)
        try {
            session.connect()
        } catch (e: Throwable) {
            session.close()
            throw e
        }
        entries[profile.id] = Entry(factory, config, session)
        session
    }

    /**
     * Closes and forgets the session. Called when its screen goes away: some firmwares allow a
     * single management session, so an idle one would lock the user out of the router.
     */
    fun release(profileId: String) {
        scope.launch {
            mutex.withLock { entries.remove(profileId) }?.session?.close()
        }
    }
}
