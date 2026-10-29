package dev.routerhub.app.ui.router

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.routerhub.api.Capability
import dev.routerhub.api.ConnectedClient
import dev.routerhub.api.ConnectionConfig
import dev.routerhub.api.DslStats
import dev.routerhub.api.RouterException
import dev.routerhub.api.RouterFirmwareFactory
import dev.routerhub.api.RouterStatus
import dev.routerhub.api.SettingsPageInfo
import dev.routerhub.api.SshHostKeyUnknownException
import dev.routerhub.app.AppContainer
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
    val settingsPages: List<SettingsPageInfo> = emptyList(),
    val error: String? = null,
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
                if (profile != null && factory != null && (config != loadedConfig || factory !== loadedFactory)) {
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
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            try {
                val session = container.sessions.obtain(profile, factory)
                val status = session.status()
                _state.update { it.copy(status = status) }
                val dsl = optional(Capability.DSL_STATS, factory) { session.dslStats() }
                val clients = optional(Capability.CONNECTED_CLIENTS, factory) { session.connectedClients() }
                val pages = optional(Capability.SETTINGS, factory) { session.settingsPages() }.orEmpty()
                _state.update { it.copy(dsl = dsl, clients = clients, settingsPages = pages) }
            } catch (e: SshHostKeyUnknownException) {
                _state.update { it.copy(hostKeyPrompt = e.presentedFingerprint) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RouterException) {
                Log.w(TAG, "Refreshing ${profile.id} failed", e)
                _state.update { it.copy(error = friendlyError(e)) }
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun reboot() = action(success = "Rebooting. The router will be back in a minute or two") { session ->
        session.reboot()
        container.sessions.release(profileId) // the connection dies with the router
    }

    fun setWifiEnabled(radioId: String, enabled: Boolean) =
        action(success = if (enabled) "Wi-Fi turned on" else "Wi-Fi turned off") { session ->
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

    fun dismissHostKey() = _state.update { it.copy(hostKeyPrompt = null, error = "Host key not trusted") }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun delete() {
        container.sessions.release(profileId)
        container.routers.delete(profileId)
    }

    override fun onCleared() {
        // Free the router's (often single) management session as soon as the screen goes away.
        container.sessions.release(profileId)
    }

    private fun action(success: String, block: suspend (FirmwareSession) -> Unit) {
        val profile = _state.value.profile ?: return
        val factory = _state.value.factory ?: return
        viewModelScope.launch {
            try {
                block(container.sessions.obtain(profile, factory))
                _state.update { it.copy(message = success) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RouterException) {
                Log.w(TAG, "Action on $profileId failed", e)
                _state.update { it.copy(message = friendlyError(e)) }
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
