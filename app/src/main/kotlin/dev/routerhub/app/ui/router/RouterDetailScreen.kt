package dev.routerhub.app.ui.router

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.routerhub.api.Capability
import dev.routerhub.api.ClientAction
import dev.routerhub.api.ConnectedClient
import dev.routerhub.api.ConnectionMedium
import dev.routerhub.api.DslLineState
import dev.routerhub.api.DslStats
import dev.routerhub.api.Protocol
import dev.routerhub.api.RouterStatus
import dev.routerhub.api.SettingsPageInfo
import dev.routerhub.api.WifiBand
import dev.routerhub.api.WifiRadio
import dev.routerhub.app.AppContainer
import dev.routerhub.app.R
import dev.routerhub.app.data.RouterProfile
import dev.routerhub.app.ui.components.EmptyScreen
import dev.routerhub.app.ui.components.InfoRow
import dev.routerhub.app.ui.components.ProtocolBadge
import dev.routerhub.app.ui.components.RouterCover
import dev.routerhub.app.ui.components.SectionHeader
import dev.routerhub.app.ui.components.StatusDot
import dev.routerhub.app.ui.formatDb
import dev.routerhub.app.ui.formatKbps
import dev.routerhub.app.ui.formatUptime
import kotlinx.coroutines.launch

/**
 * One router, in tabs: Device first, laid out like Mihon's manga page, then Wireless, Clients and the
 * router's settings by group. A tab appears only when the router has something for it.
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
    var askPassword by remember { mutableStateOf(true) }

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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = factory != null && !state.refreshing) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_router)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            enabled = profile != null,
                            onClick = { menuOpen = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_router)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            profile == null -> EmptyScreen(stringResource(R.string.router_gone), Modifier.padding(padding))
            factory == null && state.extensionsReady -> EmptyScreen(
                stringResource(R.string.detail_extension_missing, profile.firmwareId),
                Modifier.padding(padding),
            )
            factory == null -> Unit // still scanning extensions
            profile.passwordNeeded -> EmptyScreen(
                stringResource(R.string.password_needed, profile.name),
                Modifier.padding(padding),
                action = {
                    FilledTonalButton(onClick = { askPassword = true }) { Text(stringResource(R.string.enter_password)) }
                },
            )
            else -> {
                val pages = state.settingsPages.groupBy { it.group }
                val wireless = stringResource(R.string.tab_wireless)
                val network = stringResource(R.string.tab_network)
                val firewall = stringResource(R.string.tab_firewall)
                val system = stringResource(R.string.tab_system)
                val errorItem: LazyListScope.() -> Unit = {
                    state.error?.let { error -> item { ErrorCard(error, onRetry = viewModel::refresh) } }
                }
                val tabs = buildList {
                    add(
                        RouterTab(stringResource(R.string.tab_device)) {
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
                            errorItem()
                            state.status?.let { status -> overviewSection(status) }
                            state.dsl?.let { dsl -> dslSection(dsl) }
                        },
                    )
                    val radios = state.status?.wifiRadios.orEmpty()
                    if (radios.isNotEmpty() || wireless in pages) {
                        add(
                            RouterTab(wireless) {
                                errorItem()
                                items(radios, key = { "radio:${it.id}" }) { radio ->
                                    WifiRow(
                                        radio = radio,
                                        canToggle = canToggleWifi,
                                        onToggle = { enabled ->
                                            if (enabled) viewModel.setWifiEnabled(radio.id, true) else confirmWifiOff = radio
                                        },
                                    )
                                }
                                settingsItems(pages[wireless].orEmpty(), onOpenSettings)
                            },
                        )
                    }
                    if (Capability.CONNECTED_CLIENTS in factory.capabilities) {
                        add(
                            RouterTab(stringResource(R.string.tab_clients)) {
                                errorItem()
                                state.clients?.let { clients ->
                                    item { SectionHeader(pluralStringResource(R.plurals.clients_header, clients.size, clients.size)) }
                                    if (clients.isEmpty()) {
                                        item {
                                            Text(
                                                stringResource(R.string.no_clients),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                            )
                                        }
                                    }
                                    items(clients, key = { "client:${it.macAddress}" }) { client ->
                                        ClientRow(client, onClick = { viewModel.selectClient(client) })
                                    }
                                }
                            },
                        )
                    }
                    // A group of an extension's own gets a tab of its own, after the usual ones.
                    (listOf(network, firewall, system).filter { it in pages } + (pages.keys - setOf(wireless, network, firewall, system))).forEach { group ->
                        add(
                            RouterTab(group) {
                                errorItem()
                                settingsItems(pages.getValue(group), onOpenSettings)
                            },
                        )
                    }
                }
                val pager = rememberPagerState(pageCount = { tabs.size })
                val scope = rememberCoroutineScope()
                Column(Modifier.padding(padding).fillMaxSize()) {
                    PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage.coerceAtMost(tabs.lastIndex), edgePadding = 8.dp) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = pager.currentPage == index,
                                onClick = { scope.launch { pager.animateScrollToPage(index) } },
                                text = { Text(tab.title) },
                            )
                        }
                    }
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                                tabs.getOrNull(page)?.content?.invoke(this)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmReboot) {
        ConfirmDialog(
            title = stringResource(R.string.reboot_title),
            text = stringResource(R.string.reboot_text),
            confirm = stringResource(R.string.reboot),
            onConfirm = { confirmReboot = false; viewModel.reboot() },
            onDismiss = { confirmReboot = false },
        )
    }
    confirmWifiOff?.let { radio ->
        ConfirmDialog(
            title = stringResource(R.string.wifi_off_title),
            text = stringResource(R.string.wifi_off_text, radio.ssid ?: radio.id),
            confirm = stringResource(R.string.turn_off),
            onConfirm = { confirmWifiOff = null; viewModel.setWifiEnabled(radio.id, false) },
            onDismiss = { confirmWifiOff = null },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title),
            text = stringResource(R.string.delete_text),
            confirm = stringResource(R.string.delete),
            onConfirm = { confirmDelete = false; viewModel.delete(); onBack() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (askPassword && profile?.passwordNeeded == true && factory != null) {
        PasswordDialog(profile, onSave = viewModel::setPassword, onDismiss = { askPassword = false })
    }
    state.selectedClient?.let { client ->
        ClientSheet(
            client = client,
            actions = state.clientActions,
            onCopied = viewModel::copied,
            onRun = { viewModel.runClientAction(client, it) },
            onDismiss = viewModel::dismissClient,
        )
    }
    state.hostKeyPrompt?.let { fingerprint ->
        AlertDialog(
            onDismissRequest = viewModel::dismissHostKey,
            title = { Text(stringResource(R.string.host_key_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.host_key_text, profile?.host.orEmpty()))
                    Text(fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::trustHostKey) { Text(stringResource(R.string.trust)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissHostKey) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Names the address too: the file an imported router came from may not be the user's own. */
@Composable
private fun PasswordDialog(profile: RouterProfile, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.password_for, profile.username, profile.host, profile.port))
                OutlinedTextField(
                    password, { password = it },
                    label = { Text(stringResource(R.string.field_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(password) }) { Text(stringResource(R.string.connect)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private class RouterTab(val title: String, val content: LazyListScope.() -> Unit)

private fun LazyListScope.settingsItems(pages: List<SettingsPageInfo>, onOpen: (pageId: String) -> Unit) {
    items(pages, key = { "settings:${it.id}" }) { page ->
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(page.title) },
            supportingContent = page.summary?.let { { Text(it) } },
            trailingContent = {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            },
            modifier = Modifier.clickable { onOpen(page.id) },
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
                    true -> Color(0xFF2E9E5B) to if (wanConnected == false) stringResource(R.string.status_online_no_internet) else stringResource(R.string.status_online)
                    false -> MaterialTheme.colorScheme.error to stringResource(R.string.status_unavailable)
                    null -> MaterialTheme.colorScheme.outline to stringResource(R.string.status_connecting)
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
        ActionButton(Icons.Outlined.Refresh, if (refreshing) stringResource(R.string.refreshing) else stringResource(R.string.refresh), enabled = !refreshing, onClick = onRefresh)
        ActionButton(Icons.Outlined.PowerSettingsNew, stringResource(R.string.reboot), enabled = canReboot, onClick = onReboot)
        ActionButton(Icons.Outlined.Public, stringResource(R.string.web_ui), enabled = true, onClick = onWebUi)
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
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

private fun LazyListScope.overviewSection(status: RouterStatus) {
    item { SectionHeader(stringResource(R.string.overview)) }
    item {
        Column {
            status.firmwareVersion?.let { InfoRow(stringResource(R.string.info_firmware), it) }
            status.model?.let { InfoRow(stringResource(R.string.info_model), it) }
            status.uptimeSeconds?.let { InfoRow(stringResource(R.string.info_uptime), formatUptime(it)) }
            status.wanConnected?.let { InfoRow(stringResource(R.string.info_internet), if (it) stringResource(R.string.connected) else stringResource(R.string.disconnected)) }
            status.wanIpv4?.let { InfoRow(stringResource(R.string.info_wan), it) }
            status.extras.forEach { (label, value) -> InfoRow(label, value) }
        }
    }
}

private fun LazyListScope.dslSection(dsl: DslStats) {
    item { SectionHeader(stringResource(R.string.dsl_line)) }
    item {
        Column {
        InfoRow(
                stringResource(R.string.dsl_line_state),
                when (dsl.lineState) {
                    DslLineState.SHOWTIME -> stringResource(R.string.connected)
                    DslLineState.TRAINING -> stringResource(R.string.dsl_training)
                    DslLineState.DOWN -> stringResource(R.string.dsl_down)
                    DslLineState.UNKNOWN -> stringResource(R.string.dsl_unknown)
                },
            )
            if (dsl.lineState == DslLineState.SHOWTIME) {
                InfoRow(stringResource(R.string.dsl_speed), "↓ ${formatKbps(dsl.downstreamKbps)}  ↑ ${formatKbps(dsl.upstreamKbps)}")
                val maxDown = dsl.attainableDownstreamKbps
                val maxUp = dsl.attainableUpstreamKbps
                if (maxDown != null && maxUp != null) InfoRow(stringResource(R.string.dsl_attainable), "↓ ${formatKbps(maxDown)}  ↑ ${formatKbps(maxUp)}")
                dsl.snrMarginDownDb?.let { down -> InfoRow(stringResource(R.string.dsl_snr), "↓ ${formatDb(down)}  ↑ ${dsl.snrMarginUpDb?.let { formatDb(it) } ?: "–"}") }
                dsl.attenuationDownDb?.let { down -> InfoRow(stringResource(R.string.dsl_attenuation), "↓ ${formatDb(down)}  ↑ ${dsl.attenuationUpDb?.let { formatDb(it) } ?: "–"}") }
            }
            dsl.modulation?.let { InfoRow(stringResource(R.string.dsl_mode), it) }
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
                stringResource(
                    if (radio.enabled) R.string.wifi_band_on else R.string.wifi_band_off,
                    when (radio.band) {
                        WifiBand.GHZ_2_4 -> stringResource(R.string.band_2_4)
                        WifiBand.GHZ_5 -> stringResource(R.string.band_5)
                        WifiBand.GHZ_6 -> stringResource(R.string.band_6)
                        WifiBand.UNKNOWN -> radio.id
                    },
                ),
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
private fun ClientRow(client: ConnectedClient, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
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
                when {
                    client.internetBlocked -> stringResource(R.string.client_blocked)
                    client.medium == ConnectionMedium.WIRED -> stringResource(R.string.client_wired)
                    client.medium == ConnectionMedium.WIRELESS -> stringResource(R.string.wifi)
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (client.internetBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** Copying works on every router. The rest is what the router's extension offers for this client. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientSheet(
    client: ConnectedClient,
    actions: List<ClientAction>?,
    onCopied: () -> Unit,
    onRun: (ClientAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var confirming by remember { mutableStateOf<ClientAction?>(null) }
    fun copy(label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
        onCopied()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                client.hostname ?: client.macAddress,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            CopyRow(stringResource(R.string.client_copy_mac), client.macAddress) { copy("MAC address", client.macAddress) }
            client.ipAddress?.let { ip -> CopyRow(stringResource(R.string.client_copy_ip), ip) { copy("IP address", ip) } }
            client.hostname?.let { name -> CopyRow(stringResource(R.string.client_copy_name), name) { copy("Name", name) } }
            when {
                actions == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                actions.isNotEmpty() -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            actions.orEmpty().forEach { action ->
                ListItem(
                    modifier = Modifier.clickable { if (action.confirmation != null) confirming = action else onRun(action) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(action.title) },
                    supportingContent = action.summary?.let { summary -> { Text(summary) } },
                )
            }
        }
    }
    confirming?.let { action ->
        ConfirmDialog(
            title = action.title,
            text = action.confirmation.orEmpty(),
            confirm = stringResource(R.string.client_action_confirm),
            onConfirm = {
                confirming = null
                onRun(action)
            },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun CopyRow(title: String, value: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        leadingContent = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
