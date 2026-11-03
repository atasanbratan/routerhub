package dev.routerhub.app.ui.router

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.routerhub.api.Capability
import dev.routerhub.api.ClientAction
import dev.routerhub.api.ConnectedClient
import dev.routerhub.api.ConnectionConfig
import dev.routerhub.api.DslStats
import dev.routerhub.api.RouterException
import dev.routerhub.api.RouterFirmwareFactory
import dev.routerhub.api.WrongCredentialsException
import dev.routerhub.api.RouterStatus
import dev.routerhub.api.SettingsPageInfo
import dev.routerhub.api.SshHostKeyUnknownException
import dev.routerhub.app.AppContainer
import dev.routerhub.app.R
import dev.routerhub.app.data.DeviceBook
import dev.routerhub.app.data.RouterProfile
import dev.routerhub.app.data.toConnectionConfig
import dev.routerhub.app.engine.FirmwareSession
import dev.routerhub.app.ui.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RouterDetailState(
    val profile: RouterProfile? = null,
    val factory: RouterFirmwareFactory? = null,
    /** False until extensions were scanned once; avoids flashing "extension missing". */
    val extensionsReady: Boolean = false,
    val refreshing: Boolean = false,
    val status: RouterStatus? = null,
    val dsl: DslStats? = null,
    val clients: List<ConnectedClient>? = null,
    /** The client whose sheet is open. */
    val selectedClient: ConnectedClient? = null,
    /** What the router offers for [selectedClient]; `null` while asking. */
    val clientActions: List<ClientAction>? = null,
    val settingsPages: List<SettingsPageInfo> = emptyList(),
    val error: String? = null,
    /** [error] is a refused login, so the screen can offer help with the password. */
    val loginFailed: Boolean = false,
    /** SSH first contact: fingerprint waiting for the user's confirmation. */
    val hostKeyPrompt: String? = null,
    /** One-shot snackbar text. */
    val message: String? = null,
)

class RouterDetailViewModel(
    private val profileId: String,
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(RouterDetailState())
    val state: StateFlow<RouterDetailState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    /** Connection settings the shown data came from; a different value means "reconnect". */
    private var loadedConfig: ConnectionConfig? = null
    private var loadedFactory: RouterFirmwareFactory? = null

    init {
        viewModelScope.launch {
            container.routerChanges.collect { if (it == profileId) refresh() }
        }
        viewModelScope.launch {
            combine(
                container.routers.profiles,
                container.extensions.firmwares,
                container.extensions.ready,
            ) { profiles, firmwares, ready ->
                val profile = profiles.firstOrNull { it.id == profileId }
                Triple(profile, profile?.let { firmwares[it.firmwareId] }, ready)
            }.collect { (profile, factory, ready) ->
                _state.update { it.copy(profile = profile, factory = factory, extensionsReady = ready) }
                // First load, an edited address/credential, a newly pinned host key, or a reloaded
                // extension: all of them need a fresh connection.
                val config = profile?.toConnectionConfig()
                if (profile != null && factory != null && !profile.passwordNeeded && (config != loadedConfig || factory !== loadedFactory)) {
                    loadedConfig = config
                    loadedFactory = factory
                    _state.update { it.copy(status = null, dsl = null, clients = null, error = null) }
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        val profile = _state.value.profile ?: return
        val factory = _state.value.factory ?: return
        if (profile.passwordNeeded) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null, loginFailed = false) }
            try {
                val session = container.sessions.obtain(profile, factory)
                val status = session.status()
                _state.update { it.copy(status = status) }
                val dsl = optional(Capability.DSL_STATS, factory) { session.dslStats() }
                val clients = optional(Capability.CONNECTED_CLIENTS, factory) { session.connectedClients() }
                val pages = optional(Capability.SETTINGS, factory) { session.settingsPages() }.orEmpty()
                _state.update { it.copy(dsl = dsl, clients = clients, settingsPages = pages) }
                remember(clients)
            } catch (e: SshHostKeyUnknownException) {
                _state.update { it.copy(hostKeyPrompt = e.presentedFingerprint) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RouterException) {
                Log.w(TAG, "Refreshing ${profile.id} failed", e)
                _state.update { it.copy(error = friendlyError(container.context, e), loginFailed = e is WrongCredentialsException) }
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun reboot() = action(success = R.string.rebooting) { session ->
        session.reboot()
        container.sessions.release(profileId) // the connection dies with the router
    }

    fun selectClient(client: ConnectedClient) {
        _state.update { it.copy(selectedClient = client, clientActions = null) }
        val profile = _state.value.profile ?: return
        val factory = _state.value.factory ?: return
        if (Capability.CLIENT_ACTIONS !in factory.capabilities) {
            _state.update { it.copy(clientActions = emptyList()) }
            return
        }
        viewModelScope.launch {
            val actions = try {
                container.sessions.obtain(profile, factory).clientActions(client)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RouterException) {
                Log.w(TAG, "Reading the actions of a client failed", e)
                emptyList()
            }
            _state.update { if (it.selectedClient == client) it.copy(clientActions = actions) else it }
        }
    }

    /** Sets the name the user gave a device; a blank one goes back to the router's name. */
    fun renameDevice(mac: String, alias: String) =
        container.routers.updateDevices(profileId) { devices -> DeviceBook.update(devices, mac) { it.copy(alias = alias.trim().ifEmpty { null }) } }

    fun setDeviceKnown(mac: String, known: Boolean) =
        container.routers.updateDevices(profileId) { devices -> DeviceBook.update(devices, mac) { it.copy(known = known) } }

    fun forgetDevice(mac: String) {
        container.routers.updateDevices(profileId) { devices -> devices.filterNot { it.mac == mac } }
        dismissClient()
    }

    private fun remember(clients: List<ConnectedClient>?) {
        if (clients != null) container.routers.updateDevices(profileId) { DeviceBook.record(it, clients, System.currentTimeMillis()) }
    }

    fun dismissClient() = _state.update { it.copy(selectedClient = null, clientActions = null) }

    fun copied() = _state.update { it.copy(message = container.context.getString(R.string.copied)) }

    fun runClientAction(client: ConnectedClient, action: ClientAction) {
        _state.update { it.copy(selectedClient = null, clientActions = null, message = container.context.getString(R.string.client_action_running)) }
        action(success = R.string.client_action_done) { session ->
            session.runClientAction(client, action.id)
            val clients = session.connectedClients()
            _state.update { it.copy(clients = clients) }
            remember(clients)
        }
    }

    fun setWifiEnabled(radioId: String, enabled: Boolean) =
        action(success = if (enabled) R.string.wifi_turned_on else R.string.wifi_turned_off) { session ->
            session.setWifiEnabled(radioId, enabled)
            _state.update { it.copy(status = session.status()) }
        }

    /** The user confirmed the SSH host key: pin it in the profile and connect again. */
    fun trustHostKey() {
        val fingerprint = _state.value.hostKeyPrompt ?: return
        val profile = _state.value.profile ?: return
        _state.update { it.copy(hostKeyPrompt = null) }
        // The changed profile re-emits from the repository, which triggers a fresh connection.
        container.routers.upsert(profile.copy(hostKeyFingerprint = fingerprint))
    }

    fun dismissHostKey() = _state.update { it.copy(hostKeyPrompt = null, error = container.context.getString(R.string.host_key_not_trusted)) }

    /** The password for an imported router. The changed profile triggers the first connection. */
    fun setPassword(password: String) {
        val profile = _state.value.profile ?: return
        container.routers.upsert(profile.copy(password = password, passwordNeeded = false))
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun delete() {
        container.sessions.release(profileId)
        container.routers.delete(profileId)
    }

    override fun onCleared() {
        // Free the router's (often single) management session as soon as the screen goes away.
        container.sessions.release(profileId)
    }

    private fun action(@StringRes success: Int, block: suspend (FirmwareSession) -> Unit) {
        val profile = _state.value.profile ?: return
        val factory = _state.value.factory ?: return
        viewModelScope.launch {
            try {
                block(container.sessions.obtain(profile, factory))
                _state.update { it.copy(message = container.context.getString(success)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RouterException) {
                Log.w(TAG, "Action on $profileId failed", e)
                _state.update { it.copy(message = friendlyError(container.context, e)) }
            }
        }
    }

    /** Fetches an optional section; its failure shouldn't hide the sections that did load. */
    private suspend fun <T> optional(capability: Capability, factory: RouterFirmwareFactory, block: suspend () -> T): T? {
        if (capability !in factory.capabilities) return null
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: RouterException) {
            null
        }
    }

    companion object {
        private const val TAG = "RouterDetail"

        fun factory(profileId: String, container: AppContainer) = viewModelFactory {
            initializer { RouterDetailViewModel(profileId, container) }
        }
    }
}
