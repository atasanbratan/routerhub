package dev.routerhub.app.ui.browse

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.routerhub.app.AppContainer
import dev.routerhub.app.R
import dev.routerhub.app.extension.ExtensionState
import dev.routerhub.app.ui.components.InfoRow
import dev.routerhub.app.ui.components.ProtocolBadge
import dev.routerhub.app.ui.components.RouterCover
import dev.routerhub.app.ui.components.SectionHeader
import dev.routerhub.app.ui.firmwareTitle

/** Mihon's extension details: what it is, Uninstall and App info, and the router types it adds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionDetailsScreen(container: AppContainer, packageName: String, onBack: () -> Unit) {
    val extensions by container.extensions.extensions.collectAsStateWithLifecycle()
    val state = extensions.firstOrNull { it.info.packageName == packageName }
    // Uninstalled from here or elsewhere: like Mihon, the page goes away with it.
    LaunchedEffect(state == null) { if (state == null) onBack() }
    if (state == null) return
    val context = LocalContext.current
    val packageUri = "package:$packageName".toUri()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.extension_info)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "header") {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RouterCover(size = 72.dp, icon = Icons.Outlined.Extension)
                    Text(shownLabel(state), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "actions") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Android asks to confirm; the package scan then removes it from the list.
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_DELETE, packageUri)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.extension_uninstall)) }
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.extension_app_info)) }
                }
            }
            item(key = "info") {
                Column(Modifier.padding(top = 8.dp)) {
                    InfoRow(stringResource(R.string.version), state.info.versionName)
                    InfoRow(stringResource(R.string.trust), statusLine(state))
                    InfoRow("SHA-256", state.info.signerSha256.firstOrNull()?.take(16)?.plus("…") ?: stringResource(R.string.extension_unsigned))
                }
            }
            val factories = (state as? ExtensionState.Loaded)?.factories.orEmpty()
            if (factories.isNotEmpty()) {
                item(key = "firmwares") { SectionHeader(stringResource(R.string.firmwares)) }
                items(factories, key = { it.id }) { factory ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RouterCover(size = 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(firmwareTitle(factory), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                pluralStringResource(R.plurals.firmware_features, factory.capabilities.size, factory.capabilities.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ProtocolBadge(factory.protocol)
                    }
                }
            }
        }
    }
}
