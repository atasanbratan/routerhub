package dev.routerhub.app.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.routerhub.app.R

/**
 * Asks for the phone's fingerprint, face or screen lock, and calls [onSuccess] once it is given.
 * Android 9 has no prompt that falls back to the screen lock, so there it asks for the screen lock,
 * whose screen also takes a fingerprint on most phones.
 */
@Composable
fun rememberDeviceAuth(onSuccess: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val title = stringResource(R.string.lock_prompt)
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val screenLock = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) currentOnSuccess()
    }
    return remember(context, title) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val builder = BiometricPrompt.Builder(context).setTitle(title)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setDeviceCredentialAllowed(true)
                }
                builder.build().authenticate(
                    CancellationSignal(),
                    context.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = currentOnSuccess()
                    },
                )
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(KeyguardManager::class.java)
                    ?.createConfirmDeviceCredentialIntent(title, null)
                    ?.let(screenLock::launch)
            }
        }
    }
}

/** Covers the app until the phone's owner unlocks it. Asks straight away, and again on the button. */
@Composable
fun LockScreen(onUnlocked: () -> Unit, onLockUnavailable: () -> Unit) {
    val context = LocalContext.current
    val unlock = rememberDeviceAuth(onUnlocked)
    LaunchedEffect(Unit) {
        // The screen lock was removed since: nobody could unlock, so the lock goes instead.
        if (AppLock.available(context)) unlock() else onLockUnavailable()
    }
    // A Material Surface takes every touch, so nothing behind it can be used while locked.
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
            FilledTonalButton(onClick = unlock) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}
