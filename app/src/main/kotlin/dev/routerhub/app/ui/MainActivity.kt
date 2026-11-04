package dev.routerhub.app.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.routerhub.app.RouterHubApp
import dev.routerhub.app.alerts.NewDeviceAlerts
import dev.routerhub.app.ui.lock.LockScreen
import dev.routerhub.app.update.UpdateChecks
import dev.routerhub.app.ui.theme.RouterHubTheme
import dev.routerhub.app.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as RouterHubApp).container
        // A new-device notification names the router to open; only on a fresh start, not after rotation.
        val openRouter = intent.getStringExtra(NewDeviceAlerts.EXTRA_ROUTER_ID).takeIf { savedInstanceState == null }
        // An update notification says what to open: the extension updates, or the new version.
        val open = intent.getStringExtra(UpdateChecks.EXTRA_OPEN).takeIf { savedInstanceState == null }
        setContent {
            val themeMode by container.preferences.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by container.preferences.dynamicColor.collectAsStateWithLifecycle()
            val pureBlack by container.preferences.pureBlack.collectAsStateWithLifecycle()
            val dark = themeMode.isDark()

            // Status/navigation bar icons must follow the IN-APP theme, not the system one,
            // otherwise forcing dark mode on a light system leaves dark icons on a dark app bar.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }

            val lockOn by container.preferences.appLock.collectAsStateWithLifecycle()
            val locked by container.appLock.locked.collectAsStateWithLifecycle()
            LaunchedEffect(lockOn) {
                // Keeps router details out of the recent apps preview while the lock is on.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!lockOn)
            }

            RouterHubTheme(darkTheme = dark, dynamicColor = dynamicColor, pureBlack = pureBlack) {
                Box {
                    // Hidden from screen readers too while locked, not just covered.
                    Box(if (locked) Modifier.clearAndSetSemantics {} else Modifier) {
                        RouterHubNavHost(container, openRouter, open)
                    }
                    if (locked) {
                        LockScreen(
                            onUnlocked = container.appLock::unlock,
                            onLockUnavailable = {
                                container.preferences.setAppLock(false)
                                container.appLock.unlock()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as RouterHubApp).container.appLock.onStart()
    }

    override fun onStop() {
        super.onStop()
        (application as RouterHubApp).container.appLock.onStop()
    }

    private companion object {
        // Same scrims androidx.activity uses for 3-button navigation.
        val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
