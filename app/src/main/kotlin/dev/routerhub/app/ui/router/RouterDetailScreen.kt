package dev.routerhub.app.ui.router

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.routerhub.api.Capability
import dev.routerhub.api.ConnectedClient
import dev.routerhub.api.ConnectionMedium
import dev.routerhub.api.DslLineState
import dev.routerhub.api.DslStats
import dev.routerhub.api.Protocol
import dev.routerhub.api.RouterStatus
import dev.routerhub.api.WifiBand
import dev.routerhub.api.WifiRadio
import dev.routerhub.app.AppContainer
import dev.routerhub.app.ui.components.EmptyScreen
import dev.routerhub.app.ui.components.InfoRow
import dev.routerhub.app.ui.components.ProtocolBadge
import dev.routerhub.app.ui.components.RouterCover
import dev.routerhub.app.ui.components.SectionHeader
import dev.routerhub.app.ui.components.StatusDot
import dev.routerhub.app.ui.formatDb
import dev.routerhub.app.ui.formatKbps
import dev.routerhub.app.ui.formatUptime

/**
 * One router, laid out like Mihon's manga page: a cover-and-title header, a row of actions, then
 * the "chapters": overview, DSL line, Wi-Fi radios and connected clients.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterDetailScreen(
    container: AppContainer,
    profileId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenSettings: (pageId: String) -> Unit,
) {
    val viewModel: RouterDetailViewModel = viewModel(factory = RouterDetailViewModel.factory(profileId, container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var menuOpen by remember { mutableStateOf(false) }
    var confirmReboot by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmWifiOff by remember { mutableStateOf<WifiRadio?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val profile = state.profile
    val factory = state.factory
    val canToggleWifi = factory?.capabilities?.contains(Capability.WIFI_TOGGLE) == true

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = factory != null && !state.refreshing) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit router") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            enabled = profile != null,
                            onClick = { menuOpen = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete router") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            profile == null -> EmptyScreen("This router no longer exists.", Modifier.padding(padding))
            factory == null && state.extensionsReady -> EmptyScreen(
                "The extension for this router (${profile.firmwareId}) isn't installed or isn't trusted.",
                Modifier.padding(padding),
            )
            factory == null -> Unit // still scanning extensions
            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Header(
                            name = profile.name,
                            firmware = if (factory.displayName.startsWith(factory.vendor)) factory.displayName
                            else "${factory.vendor} ${factory.displayName}",
                            protocol = profile.protocol,
                            address = "${profile.host}:${profile.port}",
                            online = when {
                                state.error != null -> false
                                state.status != null -> true
                                else -> null
                            },
                            wanConnected = state.status?.wanConnected,
                        )
                    }
                    item {
                        ActionRow(
                            refreshing = state.refreshing,
                            canReboot = Capability.REBOOT in factory.capabilities,
                            onRefresh = viewModel::refresh,
                            onReboot = { confirmReboot = true },
                            onWebUi = {
                                val scheme = if (profile.protocol == Protocol.WEB && profile.useHttps) "https" else "http"
                                val port = if (profile.protocol == Protocol.WEB) ":${profile.port}" else ""
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, "$scheme://${profile.host}$port".toUri()))
                                } catch (_: ActivityNotFoundException) {
                                }
                            },
                        )
                    }
                    state.error?.let { error -> item { ErrorCard(error, onRetry = viewModel::refresh) } }
                    state.status?.let { status -> overviewSection(status) }
                    state.dsl?.let { dsl -> dslSection(dsl) }
                    state.status?.wifiRadios?.takeIf { it.isNotEmpty() }?.let { radios ->
                        item { SectionHeader("Wi-Fi") }
                        items(radios, key = { "radio:${it.id}" }) { radio ->
                            WifiRow(
                                radio = radio,
                                canToggle = canToggleWifi,
                                onToggle = { enabled ->
                                    if (enabled) viewModel.setWifiEnabled(radio.id, true) else confirmWifiOff = radio
                                },
                            )
                        }
                    }
                    state.settingsPages.groupBy { it.group }.forEach { (group, pages) ->
                        item(key = "settings-header:$group") { SectionHeader("$group settings") }
                        items(pages, key = { "settings:${it.id}" }) { page ->
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text(page.title) },
                                supportingContent = page.summary?.let { { Text(it) } },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                                },
                                modifier = Modifier.clickable { onOpenSettings(page.id) },
                            )
                        }
                    }
                    state.clients?.let { clients ->
                        item { SectionHeader("Clients (${clients.size})") }
                        if (clients.isEmpty()) {
                            item {
                                Text(
                                    "No devices connected right now",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                        items(clients, key = { "client:${it.macAddress}" }) { ClientRow(it) }
                    }
                }
            }
        }
    }

    if (confirmReboot) {
        ConfirmDialog(
            title = "Reboot router?",
            text = "Everyone on this network will lose connection for a minute or two.",
            confirm = "Reboot",
            onConfirm = { confirmReboot = false; viewModel.reboot() },
            onDismiss = { confirmReboot = false },
        )
    }
    confirmWifiOff?.let { radio ->
        ConfirmDialog(
            title = "Turn off Wi-Fi?",
            text = "Devices on \"${radio.ssid ?: radio.id}\" will disconnect, including this phone if it uses that network. " +
                "You may need a wired connection to turn it back on.",
            confirm = "Turn off",
            onConfirm = { confirmWifiOff = null; viewModel.setWifiEnabled(radio.id, false) },
            onDismiss = { confirmWifiOff = null },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete router?",
            text = "Its saved address and credentials are removed from this device.",
            confirm = "Delete",
            onConfirm = { confirmDelete = false; viewModel.delete(); onBack() },
            onDismiss = { confirmDelete = false },
        )
    }
    state.hostKeyPrompt?.let { fingerprint ->
        AlertDialog(
            onDismissRequest = viewModel::dismissHostKey,
            title = { Text("Trust this router?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("First connection to ${profile?.host}. Check that this SSH host key matches your router:")
                    Text(fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::trustHostKey) { Text("Trust") } },
            dismissButton = { TextButton(onClick = viewModel::dismissHostKey) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(
    name: String,
    firmware: String,
    protocol: Protocol,
    address: String,
    online: Boolean?,
    wanConnected: Boolean?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouterCover(size = 96.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Text(firmware, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtocolBadge(protocol)
                Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val (color, label) = when (online) {
                    true -> Color(0xFF2E9E5B) to if (wanConnected == false) "Online · internet down" else "Online"
                    false -> MaterialTheme.colorScheme.error to "Unavailable"
                    null -> MaterialTheme.colorScheme.outline to "Connecting…"
                }
                StatusDot(color)
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Mihon's icon-over-label action buttons under the header. */
@Composable
private fun ActionRow(
    refreshing: Boolean,
    canReboot: Boolean,
    onRefresh: () -> Unit,
    onReboot: () -> Unit,
    onWebUi: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        ActionButton(Icons.Outlined.Refresh, if (refreshing) "Refreshing" else "Refresh", enabled = !refreshing, onClick = onRefresh)
        ActionButton(Icons.Outlined.PowerSettingsNew, "Reboot", enabled = canReboot, onClick = onReboot)
        ActionButton(Icons.Outlined.Public, "Web UI", enabled = true, onClick = onWebUi)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.overviewSection(status: RouterStatus) {
    item { SectionHeader("Overview") }
    item {
        Column {
            status.firmwareVersion?.let { InfoRow("Firmware", it) }
            status.model?.let { InfoRow("Model", it) }
            status.uptimeSeconds?.let { InfoRow("Uptime", formatUptime(it)) }
            status.wanConnected?.let { InfoRow("Internet", if (it) "Connected" else "Disconnected") }
            status.wanIpv4?.let { InfoRow("WAN address", it) }
            status.extras.forEach { (label, value) -> InfoRow(label, value) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dslSection(dsl: DslStats) {
    item { SectionHeader("DSL line") }
    item {
        Column {
            InfoRow(
                "Line",
                when (dsl.lineState) {
                    DslLineState.SHOWTIME -> "Connected"
                    DslLineState.TRAINING -> "Training"
                    DslLineState.DOWN -> "Down"
                    DslLineState.UNKNOWN -> "Unknown"
                },
            )
            if (dsl.lineState == DslLineState.SHOWTIME) {
                InfoRow("Speed", "↓ ${formatKbps(dsl.downstreamKbps)}  ↑ ${formatKbps(dsl.upstreamKbps)}")
                val maxDown = dsl.attainableDownstreamKbps
                val maxUp = dsl.attainableUpstreamKbps
                if (maxDown != null && maxUp != null) InfoRow("Attainable", "↓ ${formatKbps(maxDown)}  ↑ ${formatKbps(maxUp)}")
                dsl.snrMarginDownDb?.let { down -> InfoRow("SNR margin", "↓ ${formatDb(down)}  ↑ ${dsl.snrMarginUpDb?.let(::formatDb) ?: "–"}") }
                dsl.attenuationDownDb?.let { down -> InfoRow("Attenuation", "↓ ${formatDb(down)}  ↑ ${dsl.attenuationUpDb?.let(::formatDb) ?: "–"}") }
            }
            dsl.modulation?.let { InfoRow("Mode", it) }
        }
    }
}

@Composable
private fun WifiRow(radio: WifiRadio, canToggle: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(radio.ssid ?: radio.id) },
        supportingContent = {
            Text(
                when (radio.band) {
                    WifiBand.GHZ_2_4 -> "2.4 GHz"
                    WifiBand.GHZ_5 -> "5 GHz"
                    WifiBand.GHZ_6 -> "6 GHz"
                    WifiBand.UNKNOWN -> radio.id
                } + if (radio.enabled) " · On" else " · Off",
            )
        },
        leadingContent = { Icon(if (radio.enabled) Icons.Outlined.Wifi else Icons.Outlined.WifiOff, contentDescription = null) },
        trailingContent = if (canToggle) {
            { Switch(checked = radio.enabled, onCheckedChange = onToggle) }
        } else {
            null
        },
    )
}

@Composable
private fun ClientRow(client: ConnectedClient) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(client.hostname ?: client.macAddress) },
        supportingContent = {
            Text(listOfNotNull(client.ipAddress, client.macAddress.takeIf { client.hostname != null }).joinToString(" · "))
        },
        leadingContent = {
            Icon(
                if (client.medium == ConnectionMedium.WIRELESS) Icons.Outlined.PhoneAndroid else Icons.Outlined.Computer,
                contentDescription = null,
            )
        },
        trailingContent = {
            Text(
                when (client.medium) {
                    ConnectionMedium.WIRED -> "Wired"
                    ConnectionMedium.WIRELESS -> "Wi-Fi"
                    ConnectionMedium.UNKNOWN -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
