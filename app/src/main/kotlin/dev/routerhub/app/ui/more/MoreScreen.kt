package dev.routerhub.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.routerhub.app.AppContainer
import dev.routerhub.app.BuildConfig
import dev.routerhub.app.data.ThemeMode
import dev.routerhub.app.ui.components.RouterCover

/** Mihon-style "More": a logo header, then a flat list of settings. */
@Composable
fun MoreScreen(container: AppContainer, onOpenAbout: () -> Unit) {
    val prefs = container.preferences
    val themeMode by prefs.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by prefs.dynamicColor.collectAsStateWithLifecycle()
    val pureBlack by prefs.pureBlack.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RouterCover(size = 72.dp)
                Text("RouterHub", style = MaterialTheme.typography.headlineSmall)
            }
            HorizontalDivider()

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Theme") },
                supportingContent = { Text(themeMode.label()) },
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                modifier = Modifier.clickable { showThemeDialog = true },
            )
            if (prefs.supportsDynamicColor) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Dynamic color") },
                    supportingContent = { Text("Use colors from your wallpaper") },
                    leadingContent = { Icon(Icons.Outlined.ColorLens, contentDescription = null) },
                    trailingContent = { Switch(checked = dynamicColor, onCheckedChange = prefs::setDynamicColor) },
                    modifier = Modifier.clickable { prefs.setDynamicColor(!dynamicColor) },
                )
            }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Pure black dark mode") },
                supportingContent = { Text("True black backgrounds for OLED screens") },
                leadingContent = { Icon(Icons.Outlined.DarkMode, contentDescription = null) },
                trailingContent = { Switch(checked = pureBlack, onCheckedChange = prefs::setPureBlack) },
                modifier = Modifier.clickable { prefs.setPureBlack(!pureBlack) },
            )
            HorizontalDivider()
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("About") },
                supportingContent = { Text("v${BuildConfig.VERSION_NAME}") },
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenAbout),
            )
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = mode == themeMode, role = Role.RadioButton) {
                                    prefs.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode == themeMode, onClick = null)
                            Text(mode.label(), modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Close") } },
        )
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> "Follow system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
