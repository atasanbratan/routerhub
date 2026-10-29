package dev.routerhub.app.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.routerhub.app.RouterHubApp
import dev.routerhub.app.ui.theme.RouterHubTheme
import dev.routerhub.app.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as RouterHubApp).container
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

            RouterHubTheme(darkTheme = dark, dynamicColor = dynamicColor, pureBlack = pureBlack) {
                RouterHubNavHost(container)
            }
        }
    }

    private companion object {
        // Same scrims androidx.activity uses for 3-button navigation.
        val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
