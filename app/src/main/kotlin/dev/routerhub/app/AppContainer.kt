package dev.routerhub.app

import android.content.Context
import dev.routerhub.app.data.AppPreferences
import dev.routerhub.app.data.RouterRepository
import dev.routerhub.app.data.SecretCipher
import dev.routerhub.app.engine.SessionManager
import dev.routerhub.app.extension.ExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

/** Manual dependency graph: one instance of each service for the whole process. */
class AppContainer(context: Context, scope: CoroutineScope) {
    /** Emits a router id after its configuration changed on the router, so open screens refresh. */
    val routerChanges = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val extensions: ExtensionManager = ExtensionManager.create(context, scope).also { it.start() }
    val routers = RouterRepository(context, SecretCipher())
    val sessions = SessionManager(scope)
    val preferences = AppPreferences(context)
}
