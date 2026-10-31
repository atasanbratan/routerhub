package dev.routerhub.app.ui.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.routerhub.api.FirmwareTimeoutException
import dev.routerhub.api.InvalidSettingException
import dev.routerhub.api.RouterException
import dev.routerhub.api.SettingsPage
import dev.routerhub.app.AppContainer
import dev.routerhub.app.R
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

/** The router behind a settings page. The UI tests put a fake here. */
interface SettingsSource {
    suspend fun read(): SettingsPage
    suspend fun write(changes: Map<String, String>)

    /** The router now expects [password] as its login password. */
    fun loginPasswordChanged(password: String)

    /** The router's configuration changed, so open screens should refresh. */
    fun changed()
}

private class RouterSettingsSource(
    private val profileId: String,
    private val pageId: String,
    private val container: AppContainer,
) : SettingsSource {
    override suspend fun read() = session().readSettings(pageId)

    override suspend fun write(changes: Map<String, String>) = session().writeSettings(pageId, changes)

    override fun loginPasswordChanged(password: String) {
        container.routers.get(profileId)?.let { container.routers.upsert(it.copy(password = password)) }
    }

    override fun changed() {
        container.routerChanges.tryEmit(profileId)
    }

    private suspend fun session(): FirmwareSession {
        val context = container.context
        val profile = container.routers.get(profileId) ?: throw RouterException(context.getString(R.string.router_gone))
        container.extensions.ready.first { it }
        val factory = container.extensions.firmwares.first()[profile.firmwareId]
            ?: throw RouterException(context.getString(R.string.settings_extension_missing))
        return container.sessions.obtain(profile, factory)
    }
}

class SettingsPageViewModel(private val source: SettingsSource, private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            try {
                val page = source.read()
                _state.update { it.fresh(page) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RouterException) {
                Log.w(TAG, "Reading the page failed", e)
                _state.update { it.copy(loading = false, loadError = friendlyError(context, e)) }
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

        if (unconfirmed(page, changes, current.confirmations)) return

        viewModelScope.launch {
            _state.update { it.copy(saving = true, fieldErrors = emptyMap()) }
            try {
                source.write(changes)

                // The router now expects the new login password; keep the saved profile in step.
                page.loginPasswordKey?.let { changes[it] }?.let(source::loginPasswordChanged)
                _state.update { it.copy(message = context.getString(R.string.saved)) }
                reloadAfterWrite()
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidSettingException) {
                val known = page.fields.any { it.key == e.key }
                _state.update {
                    it.copy(
                        fieldErrors = if (known) mapOf(e.key to (e.message ?: context.getString(R.string.not_accepted))) else emptyMap(),
                        message = if (known) null else e.message,
                    )
                }
            } catch (e: FirmwareTimeoutException) {
                // Restarting the radio can stall the router's web server mid-answer.
                Log.w(TAG, "Saving timed out", e)
                _state.update { it.copy(message = context.getString(R.string.save_no_answer)) }
                reloadAfterWrite()
            } catch (e: RouterException) {
                Log.w(TAG, "Saving failed", e)
                _state.update { it.copy(message = friendlyError(context, e)) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    /** Gives the radio a moment to come back, then shows what the router really holds now. */
    private suspend fun reloadAfterWrite() {
        delay(RADIO_RESTART_MILLIS)
        try {
            val page = source.read()
            _state.update { it.fresh(page) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RouterException) {
            Log.w(TAG, "Re-reading the page failed", e)
            _state.update { it.copy(message = context.getString(R.string.saved_not_reloaded)) }
        }
        source.changed()
    }

    private fun SettingsUiState.fresh(page: SettingsPage) = copy(
        loading = false, page = page, loadError = null,
        edits = emptyMap(), confirmations = emptyMap(), fieldErrors = emptyMap(),
    )

    companion object {
        private const val TAG = "SettingsPage"
        private const val RADIO_RESTART_MILLIS = 2_500L

        fun factory(profileId: String, pageId: String, container: AppContainer) = viewModelFactory {
            initializer { SettingsPageViewModel(RouterSettingsSource(profileId, pageId, container), container.context) }
        }
    }
}
