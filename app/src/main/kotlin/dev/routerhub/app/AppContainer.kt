package dev.routerhub.app

import android.content.Context
import dev.routerhub.app.data.AppPreferences
import dev.routerhub.app.data.RouterRepository
import dev.routerhub.app.data.SecretCipher
import dev.routerhub.app.engine.SessionManager
import dev.routerhub.app.extension.ExtensionManager
import dev.routerhub.app.extension.ExtensionRepos
import dev.routerhub.app.extension.ExtensionStore
import dev.routerhub.app.ui.lock.AppLock
import kotlinx.coroutines.CoroutineScope
import dev.routerhub.app.update.AppUpdateDownloader
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Manual dependency graph: one instance of each service for the whole process. */
class AppContainer(context: Context, scope: CoroutineScope) {
    /** For the text view models put in their state. */
    val context: Context = context.applicationContext

    /** Emits a router id after its configuration changed on the router, so open screens refresh. */
    val routerChanges = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val extensions: ExtensionManager = ExtensionManager.create(context, scope).also { it.start() }
    val extensionRepos = ExtensionRepos(context)

    /** Only builds that can install extensions read the repositories; the Play build never does. */
    val store = ExtensionStore(context.applicationContext, extensionRepos, scope)

    /** How many installed extensions the repositories have an update for, for the badge on Browse. */
    val extensionUpdateCount: StateFlow<Int> = combine(extensions.extensions, store.available) { installed, available ->
        ExtensionStore.updates(installed, available).size
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    val appUpdates = AppUpdateDownloader(context.applicationContext, scope)
    val routers = RouterRepository(context, SecretCipher())
    val sessions = SessionManager(scope)
    val preferences = AppPreferences(context)
    val appLock = AppLock({ preferences.appLock.value })
}
