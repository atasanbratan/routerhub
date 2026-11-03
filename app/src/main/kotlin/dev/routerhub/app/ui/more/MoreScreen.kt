package dev.routerhub.app.ui.more

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.routerhub.app.AppContainer
import dev.routerhub.app.BuildConfig
import dev.routerhub.app.R
import dev.routerhub.app.data.ThemeMode
import dev.routerhub.app.ui.components.RouterCover
import kotlinx.coroutines.launch

/** Mihon-style "More": a logo header, then a flat list of settings. */
@Composable
fun MoreScreen(container: AppContainer, onOpenAbout: () -> Unit, onOpenTrust: () -> Unit) {
    val prefs = container.preferences
    val themeMode by prefs.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by prefs.dynamicColor.collectAsStateWithLifecycle()
    val pureBlack by prefs.pureBlack.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch { toast(context, exportRouters(context, uri, container.routers.profiles.value)) }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { toast(context, importRouters(context, uri, container.routers)) }
    }

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
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            }
            HorizontalDivider()

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.theme)) },
                supportingContent = { Text(themeMode.label()) },
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                modifier = Modifier.clickable { showThemeDialog = true },
            )
            if (prefs.supportsDynamicColor) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.dynamic_color_summary)) },
                    leadingContent = { Icon(Icons.Outlined.ColorLens, contentDescription = null) },
                    trailingContent = { Switch(checked = dynamicColor, onCheckedChange = prefs::setDynamicColor) },
                    modifier = Modifier.clickable { prefs.setDynamicColor(!dynamicColor) },
                )
            }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.pure_black)) },
                supportingContent = { Text(stringResource(R.string.pure_black_summary)) },
                leadingContent = { Icon(Icons.Outlined.DarkMode, contentDescription = null) },
                trailingContent = { Switch(checked = pureBlack, onCheckedChange = prefs::setPureBlack) },
                modifier = Modifier.clickable { prefs.setPureBlack(!pureBlack) },
            )
            HorizontalDivider()
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.export_routers)) },
                supportingContent = { Text(stringResource(R.string.export_routers_summary)) },
                leadingContent = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                modifier = Modifier.clickable {
                    if (container.routers.profiles.value.isEmpty()) {
                        Toast.makeText(context, R.string.export_nothing, Toast.LENGTH_SHORT).show()
                    } else {
                        exportFile.launch(EXPORT_FILE_NAME)
                    }
                },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.import_routers)) },
                supportingContent = { Text(stringResource(R.string.import_routers_summary)) },
                leadingContent = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                modifier = Modifier.clickable { importFile.launch(arrayOf("application/json", "application/octet-stream")) },
            )
            HorizontalDivider()
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.trust_title)) },
                supportingContent = { Text(stringResource(R.string.trust_summary)) },
                leadingContent = { Icon(Icons.Outlined.Shield, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenTrust),
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.about)) },
                supportingContent = { Text("v${BuildConfig.VERSION_NAME}") },
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenAbout),
            )
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.theme)) },
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
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

private fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

@Composable
private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}
