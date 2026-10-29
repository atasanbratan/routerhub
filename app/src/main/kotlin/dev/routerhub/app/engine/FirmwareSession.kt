package dev.routerhub.app.engine

import dev.routerhub.api.Capability
import dev.routerhub.api.ConnectedClient
import dev.routerhub.api.ConnectionConfig
import dev.routerhub.api.DslStats
import dev.routerhub.api.FirmwareTimeoutException
import dev.routerhub.api.InvalidConfigurationException
import dev.routerhub.api.RouterException
import dev.routerhub.api.RouterFirmware
import dev.routerhub.api.RouterFirmwareFactory
import dev.routerhub.api.RouterSessionExpiredException
import dev.routerhub.api.RouterStatus
import dev.routerhub.api.SettingsPage
import dev.routerhub.api.SettingsPageInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** An extension broke its contract: crashed, threw a foreign exception, or failed to link. */
class ExtensionFailureException(
    val firmwareId: String,
    message: String,
    cause: Throwable,
) : RouterException(message, cause)

/**
 * The host's only entry point into extension code. It wraps a [RouterFirmware] and enforces the
 * contract the extension itself can't be trusted to keep:
 *
 *  - Serialisation: one call at a time. Embedded web servers on old routers often crash or log
 *    you out when they receive concurrent requests.
 *  - Off the main thread, with a hard timeout per call. This only works if the extension
 *    cooperates with cancellation, as RouterFirmware's contract requires.
 *  - Transparent re-login: one retry after [RouterSessionExpiredException].
 *  - Exception normalisation: the UI only ever sees [RouterException]s. Linkage errors and crashes
 *    from extension code become [ExtensionFailureException] instead of killing the app.
 *
 * What it can NOT do: isolate the process. A thread the extension starts itself can still crash
 * the app, which is why only verified extensions are loaded at all.
 */
class FirmwareSession private constructor(
    val factory: RouterFirmwareFactory,
    private val firmware: RouterFirmware,
    private val callTimeout: Duration,
) {
    private val mutex = Mutex()

    val capabilities: Set<Capability> get() = factory.capabilities

    suspend fun connect(): Unit = mutex.withLock { guard { firmware.login() } }

    suspend fun status(): RouterStatus = call { it.getStatus() }

    suspend fun dslStats(): DslStats = call { it.getDslStats() }

    suspend fun connectedClients(): List<ConnectedClient> = call { it.getConnectedClients() }

    suspend fun reboot(): Unit = call { it.reboot() }

    suspend fun setWifiEnabled(radioId: String?, enabled: Boolean): Unit =
        call { it.setWifiEnabled(radioId, enabled) }

    suspend fun settingsPages(): List<SettingsPageInfo> = call { it.listSettingsPages() }

    suspend fun readSettings(pageId: String): SettingsPage = call { it.readSettings(pageId) }

    /**
     * Unlike reads, a write is never replayed after "session expired": if the router dropped the
     * session mid-write we can't know whether it applied, so the caller re-reads and decides.
     */
    suspend fun writeSettings(pageId: String, changes: Map<String, String>): Unit =
        mutex.withLock { guard { firmware.writeSettings(pageId, changes) } }

    /** Always completes, even if the caller is being cancelled. */
    suspend fun close() {
        withContext(NonCancellable) {
            runCatching { mutex.withLock { guard { firmware.close() } } }
        }
    }

    private suspend fun <T> call(block: suspend (RouterFirmware) -> T): T = mutex.withLock {
        try {
            guard { block(firmware) }
        } catch (_: RouterSessionExpiredException) {
            guard { firmware.login() }
            guard { block(firmware) }
        }
    }

    private suspend fun <T> guard(block: suspend () -> T): T = try {
        withTimeout(callTimeout) {
            withContext(Dispatchers.IO) { block() }
        }
    } catch (e: TimeoutCancellationException) {
        throw FirmwareTimeoutException("${factory.displayName} did not answer within $callTimeout", e)
    } catch (e: CancellationException) {
        throw e // our caller was cancelled; never swallow that
    } catch (e: RouterException) {
        throw e
    } catch (e: LinkageError) {
        throw ExtensionFailureException(factory.id, "${factory.displayName} is incompatible with this app version", e)
    } catch (e: Exception) {
        throw ExtensionFailureException(factory.id, "${factory.displayName} crashed: ${e.message}", e)
    }

    companion object {
        fun open(
            factory: RouterFirmwareFactory,
            config: ConnectionConfig,
            callTimeout: Duration = 30.seconds,
        ): FirmwareSession {
            if (config.protocol != factory.protocol) {
                throw InvalidConfigurationException(
                    "${factory.displayName} needs a ${factory.protocol} configuration, got ${config.protocol}",
                )
            }
            val firmware = try {
                factory.create(config)
            } catch (e: RouterException) {
                throw e
            } catch (e: Throwable) {
                if (e is VirtualMachineError) throw e
                throw ExtensionFailureException(factory.id, "${factory.displayName} failed to start", e)
            }
            return FirmwareSession(factory, firmware, callTimeout)
        }
    }
}
