package dev.routerhub.app.ui.lock

import android.app.KeyguardManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app is locked. It locks when the process starts and when the user comes back after
 * more than [GRACE_MILLIS] away, if the lock is on. Unlocking is the phone's own business: its
 * fingerprint, face or screen lock (see [rememberDeviceAuth]).
 */
class AppLock(private val enabled: () -> Boolean, private val clock: () -> Long = System::currentTimeMillis) {
    private val _locked = MutableStateFlow(enabled())
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var leftAt: Long? = null

    /** The app went to the background. */
    fun onStop() {
        leftAt = clock()
    }

    /** The app came back. A short trip to another app, like copying a password, doesn't lock. */
    fun onStart() {
        val left = leftAt ?: return
        leftAt = null
        if (enabled() && clock() - left > GRACE_MILLIS) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    companion object {
        const val GRACE_MILLIS = 30_000L

        /** Only a phone with a screen lock can lock the app; without one nobody could unlock it again. */
        fun available(context: Context): Boolean =
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }
}
