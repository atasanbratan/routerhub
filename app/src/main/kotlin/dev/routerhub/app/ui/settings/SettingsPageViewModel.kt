package dev.routerhub.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.routerhub.api.FirmwareTimeoutException
import dev.routerhub.api.InvalidSettingException
import dev.routerhub.api.RouterException
import dev.routerhub.api.SettingField
import dev.routerhub.api.SettingsPage
import dev.routerhub.app.AppContainer
import dev.routerhub.app.engine.FirmwareSession
import dev.routerhub.app.ui.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val page: SettingsPage? = null,
    val loadError: String? = null,
    /** Raw edited values by field key; only keys the user touched. */
    val edits: Map<String, String> = emptyMap(),
    /** Second entry of secrets that must be typed twice. */
    val confirmations: Map<String, String> = emptyMap(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val message: String? = null,
)

class SettingsPageViewModel(
    private val profileId: String,
    private val pageId: String,
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            try {
                val page = session().readSettings(pageId)
                _state.update { it.fresh(page) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RouterException) {
                Log.w(TAG, "Reading $pageId failed", e)
                _state.update { it.copy(loading = false, loadError = friendlyError(e)) }
            }
        }
    }

    fun edit(key: String, value: String) =
        _state.update { it.copy(edits = it.edits + (key to value), fieldErrors = it.fieldErrors - key) }

    fun confirm(key: String, value: String) =
        _state.update { it.copy(confirmations = it.confirmations + (key to value), fieldErrors = it.fieldErrors - key) }

    fun discard() = _state.update { it.copy(edits = emptyMap(), confirmations = emptyMap(), fieldErrors = emptyMap()) }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun save() {
        val current = _state.value
        val page = current.page ?: return
        val changes = pendingChanges(page, current.edits)
        if (changes.isEmpty() || current.saving) return

        val mismatch = page.fields.filterIsInstance<SettingField.Secret>().firstOrNull {
            it.requiresConfirmation && changes.containsKey(it.key) && current.confirmations[it.key] != changes[it.key]
        }
        if (mismatch != null) {
            _state.update { it.copy(fieldErrors = mapOf(mismatch.key to "The two entries don't match")) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true, fieldErrors = emptyMap()) }
            try {
                session().writeSettings(pageId, changes)

                // The router now expects the new login password; keep the saved profile in step.
                page.loginPasswordKey?.let { key ->
                    val newPassword = changes[key]
                    val profile = container.routers.get(profileId)
                    if (newPassword != null && profile != null) {
                        container.routers.upsert(profile.copy(password = newPassword))
                    }
                }
                _state.update { it.copy(message = "Saved") }
                reloadAfterWrite()
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidSettingException) {
                val known = page.fields.any { it.key == e.key }
                _state.update {
                    it.copy(
                        fieldErrors = if (known) mapOf(e.key to (e.message ?: "Not accepted")) else emptyMap(),
                        message = if (known) null else e.message,
                    )
                }
            } catch (e: FirmwareTimeoutException) {
                // Restarting the radio can stall the router's web server mid-answer.
                Log.w(TAG, "Saving $pageId timed out", e)
                _state.update { it.copy(message = "No answer from the router. The change may still have applied.") }
                reloadAfterWrite()
            } catch (e: RouterException) {
                Log.w(TAG, "Saving $pageId failed", e)
                _state.update { it.copy(message = friendlyError(e)) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    /** Gives the radio a moment to come back, then shows what the router really holds now. */
    private suspend fun reloadAfterWrite() {
        delay(RADIO_RESTART_MILLIS)
        try {
            val page = session().readSettings(pageId)
            _state.update { it.fresh(page) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RouterException) {
            Log.w(TAG, "Re-reading $pageId failed", e)
            _state.update { it.copy(message = "Saved, but the page couldn't be reloaded yet. Pull to refresh later.") }
        }
        container.routerChanges.tryEmit(profileId)
    }

    private fun SettingsUiState.fresh(page: SettingsPage) = copy(
        loading = false, page = page, loadError = null,
        edits = emptyMap(), confirmations = emptyMap(), fieldErrors = emptyMap(),
    )

    private suspend fun session(): FirmwareSession {
        val profile = container.routers.get(profileId) ?: throw RouterException("This router no longer exists")
        container.extensions.ready.first { it }
        val factory = container.extensions.firmwares.first()[profile.firmwareId]
            ?: throw RouterException("The extension for this router isn't installed")
        return container.sessions.obtain(profile, factory)
    }

    companion object {
        private const val TAG = "SettingsPage"
        private const val RADIO_RESTART_MILLIS = 2_500L

        fun factory(profileId: String, pageId: String, container: AppContainer) = viewModelFactory {
            initializer { SettingsPageViewModel(profileId, pageId, container) }
        }
    }
}
