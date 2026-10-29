package dev.routerhub.app.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.routerhub.api.RouterFirmwareFactory
import dev.routerhub.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Single source of truth for installed extensions. Rescans whenever a package is installed,
 * updated or removed.
 */
class ExtensionManager(
    private val context: Context,
    private val discovery: ExtensionDiscovery,
    private val loader: ExtensionLoader,
    private val trustStore: UserTrustStore,
    private val scope: CoroutineScope,
) {
    private val _extensions = MutableStateFlow<List<ExtensionState>>(emptyList())
    val extensions: StateFlow<List<ExtensionState>> = _extensions.asStateFlow()

    /** False until the first scan finished, so "extension missing" isn't shown during startup. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * Every firmware the user can pick when adding a router, keyed by factory id. If two extensions
     * claim the same id, the one later in the list wins. Real code should surface that conflict.
     */
    val firmwares: Flow<Map<String, RouterFirmwareFactory>> = extensions.map { states ->
        states.filterIsInstance<ExtensionState.Loaded>()
            .flatMap { it.factories }
            .associateBy { it.id }
    }

    // Conflated: a burst of package broadcasts (e.g. a batch update) collapses into one rescan.
    private val rescanRequests = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        scope.launch(Dispatchers.IO) {
            for (request in rescanRequests) {
                _extensions.value = discovery.findInstalled()
                    .map(loader::load)
                    .sortedBy { it.info.label.lowercase() }
                _ready.value = true
            }
        }
        registerPackageReceiver()
        rescan()
    }

    fun rescan() {
        rescanRequests.trySend(Unit)
    }

    /** User accepted an extension signed by an unknown key ("Trust" button). */
    fun trust(extension: ExtensionState.Untrusted) {
        trustStore.trust(extension.info)
        rescan()
    }

    /**
     * Note on updates: a class loader can't be unloaded while anything references its classes.
     * When an extension is replaced, open FirmwareSessions keep running the OLD code until closed,
     * so the session layer should close and reopen sessions whose package changed.
     */
    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = rescan()
        }
        // These are protected broadcasts only the system can send, so EXPORTED can't be abused.
        // The handler only triggers a rescan, which re-verifies every signature anyway.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    companion object {
        fun create(context: Context, scope: CoroutineScope): ExtensionManager {
            val trustStore = UserTrustStore(context)
            val verifier = SignatureVerifier(
                officialSigners = BuildConfig.OFFICIAL_EXTENSION_SIGNERS.split(','),
                // Debug hosts also accept extensions signed with the same (debug) key, so locally
                // built extensions load without touching the official key list.
                developmentSigners =
                    if (BuildConfig.DEBUG) SignatureVerifier.hostSigners(context) else emptyList(),
                userTrust = trustStore,
            )
            return ExtensionManager(
                context = context.applicationContext,
                discovery = ExtensionDiscovery(context.packageManager),
                loader = ExtensionLoader(context.applicationContext, verifier),
                trustStore = trustStore,
                scope = scope,
            )
        }
    }
}
