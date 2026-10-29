package dev.routerhub.app.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.routerhub.app.AppContainer
import dev.routerhub.app.extension.ExtensionState
import dev.routerhub.app.extension.TrustLevel
import dev.routerhub.app.ui.components.EmptyScreen
import dev.routerhub.app.ui.components.ProtocolBadge
import dev.routerhub.app.ui.components.RouterCover

/** Mihon's Browse, translated: "Firmwares" plays the role of Sources, "Extensions" stays. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(container: AppContainer, onAddRouter: (firmwareId: String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val extensions by container.extensions.extensions.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(title = { Text("Browse") })
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Firmwares") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Extensions") })
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when (tab) {
            0 -> FirmwaresTab(extensions, onAddRouter, modifier)
            else -> ExtensionsTab(extensions, onTrust = container.extensions::trust, modifier)
        }
    }
}

@Composable
private fun FirmwaresTab(extensions: List<ExtensionState>, onAddRouter: (String) -> Unit, modifier: Modifier) {
    val loaded = extensions.filterIsInstance<ExtensionState.Loaded>()
    if (loaded.isEmpty()) {
        EmptyScreen("No firmwares yet.\nInstall a router extension to add support for your router.", modifier)
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        loaded.forEach { extension ->
            item(key = "header:${extension.info.packageName}") {
                Text(
                    extension.info.label.removePrefix("RouterHub: "),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(extension.factories, key = { it.id }) { factory ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onAddRouter(factory.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RouterCover(size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (factory.displayName.startsWith(factory.vendor)) factory.displayName
                            else "${factory.vendor} ${factory.displayName}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "${factory.capabilities.size} features",
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

@Composable
private fun ExtensionsTab(
    extensions: List<ExtensionState>,
    onTrust: (ExtensionState.Untrusted) -> Unit,
    modifier: Modifier,
) {
    if (extensions.isEmpty()) {
        EmptyScreen("No extensions installed.", modifier)
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        items(extensions, key = { it.info.packageName }) { state ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RouterCover(size = 40.dp, icon = Icons.Outlined.Extension)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(state.info.label.removePrefix("RouterHub: "), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "v${state.info.versionName} · ${statusLine(state)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state is ExtensionState.Loaded) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state is ExtensionState.Untrusted) {
                        Text(
                            "SHA-256 ${state.info.signerSha256.firstOrNull()?.take(16) ?: "unsigned"}…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state is ExtensionState.Untrusted) {
                    TextButton(onClick = { onTrust(state) }) { Text("Trust") }
                } else if (state is ExtensionState.Loaded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.factories.map { it.protocol }.distinct().forEach { ProtocolBadge(it) }
                    }
                }
            }
        }
    }
}

private fun statusLine(state: ExtensionState): String = when (state) {
    is ExtensionState.Loaded -> when (state.trust) {
        TrustLevel.OFFICIAL -> "Official"
        TrustLevel.DEVELOPMENT -> "Development build"
        TrustLevel.USER_TRUSTED -> "Trusted by you"
        TrustLevel.BUILT_IN -> "Built in"
    }
    is ExtensionState.Untrusted -> "Untrusted: unknown signer"
    is ExtensionState.Incompatible -> state.reason
    is ExtensionState.Failed -> "Failed to load: ${state.error.message ?: state.error::class.java.simpleName}"
}
