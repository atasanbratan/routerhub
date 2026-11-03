package dev.routerhub.app.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.routerhub.api.Protocol
import dev.routerhub.api.RouterFirmwareFactory
import dev.routerhub.app.AppContainer
import dev.routerhub.app.R
import dev.routerhub.app.basic.BasicFactory
import dev.routerhub.app.data.FoundRouter
import dev.routerhub.app.data.RouterFinder
import dev.routerhub.app.data.RouterProfile
import dev.routerhub.app.ui.components.EmptyScreen
import dev.routerhub.app.ui.components.LoginHelpDialog
import dev.routerhub.app.ui.components.ProtocolBadge
import dev.routerhub.app.ui.components.RouterCover
import java.util.UUID

/**
 * Adds a router (firmware picker, then connection form) or, with [editProfileId], edits a saved
 * one. In edit mode the firmware is fixed and the form starts from the saved values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRouterScreen(
    container: AppContainer,
    initialFirmwareId: String?,
    onBack: () -> Unit,
    onSaved: (profileId: String) -> Unit,
    editProfileId: String? = null,
) {
    val firmwares by container.extensions.firmwares.collectAsStateWithLifecycle(initialValue = emptyMap())
    val editing = editProfileId?.let(container.routers::get)
    var firmwareId by rememberSaveable { mutableStateOf(editing?.firmwareId ?: initialFirmwareId) }
    val factory = firmwareId?.let { firmwares[it] }
    val canChangeFirmware = editProfileId == null && initialFirmwareId == null
    val context = LocalContext.current
    var searching by remember { mutableStateOf(editProfileId == null) }
    var found by remember { mutableStateOf<FoundRouter?>(null) }
    LaunchedEffect(Unit) {
        if (editProfileId == null) {
            found = RouterFinder(context.applicationContext).find()
            searching = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            editProfileId != null -> stringResource(R.string.edit_router)
                            factory == null -> stringResource(R.string.choose_firmware)
                            else -> stringResource(R.string.add_router)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (factory != null && canChangeFirmware) firmwareId = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when {
            editProfileId != null && editing == null -> EmptyScreen(stringResource(R.string.router_gone), modifier)
            editing != null && factory == null -> EmptyScreen(
                stringResource(R.string.edit_extension_missing),
                modifier,
            )
            factory == null -> Column(modifier) {
                val suggested = found?.let { RouterFinder.suggest(it, firmwares.values) }
                FinderCard(searching, found, suggested, onUse = { firmwareId = it.id })
                FirmwarePicker(firmwares.values.toList(), onPick = { firmwareId = it.id }, modifier = Modifier.weight(1f))
            }
            else -> ConnectionForm(
                factory = factory,
                existing = editing,
                foundHost = found?.host,
                modifier = modifier,
                onSave = { profile ->
                    if (editing == null) {
                        container.routers.upsert(profile)
                    } else {
                        // The form holds a copy from when it opened; devices seen since then are kept.
                        container.routers.update(profile.id) { current ->
                            profile.copy(devices = current.devices, alertNewDevices = current.alertNewDevices)
                        }
                    }
                    onSaved(profile.id)
                },
            )
        }
    }
}

@Composable
private fun FinderCard(searching: Boolean, found: FoundRouter?, suggested: RouterFirmwareFactory?, onUse: (RouterFirmwareFactory) -> Unit) {
    if (!searching && found == null) return
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    found == null -> stringResource(R.string.finder_searching)
                    suggested != null -> stringResource(R.string.finder_found, suggested.displayName, found.host)
                    else -> stringResource(R.string.finder_found_unknown, found.host)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (suggested != null) TextButton(onClick = { onUse(suggested) }) { Text(stringResource(R.string.finder_use)) }
        }
    }
}

@Composable
private fun FirmwarePicker(factories: List<RouterFirmwareFactory>, onPick: (RouterFirmwareFactory) -> Unit, modifier: Modifier) {
    if (factories.isEmpty()) {
        EmptyScreen(stringResource(R.string.no_firmwares_available), modifier)
        return
    }
    LazyColumn(modifier) {
        items(factories.sortedWith(compareBy({ it.vendor }, { it.displayName }, { it.protocol })), key = { it.id }) { factory ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(factory) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RouterCover(size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(factory.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(factory.vendor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ProtocolBadge(factory.protocol)
            }
        }
    }
}

@Composable
private fun ConnectionForm(
    factory: RouterFirmwareFactory,
    existing: RouterProfile?,
    foundHost: String?,
    modifier: Modifier,
    onSave: (RouterProfile) -> Unit,
) {
    val title = if (factory.displayName.startsWith(factory.vendor)) factory.displayName else "${factory.vendor} ${factory.displayName}"
    var name by rememberSaveable { mutableStateOf(existing?.name ?: title) }
    var host by rememberSaveable { mutableStateOf(existing?.host ?: foundHost ?: DEFAULT_ADDRESS) }
    LaunchedEffect(foundHost) {
        if (existing == null && foundHost != null && host == DEFAULT_ADDRESS) host = foundHost
    }
    var port by rememberSaveable { mutableStateOf((existing?.port ?: factory.defaultPort).toString()) }
    var username by rememberSaveable {
        mutableStateOf(existing?.username ?: if (factory.protocol == Protocol.SSH || factory.vendor == "OpenWrt") "root" else "admin")
    }
    // When editing, the stored password is never put back on screen: blank means "keep it".
    var password by rememberSaveable { mutableStateOf("") }
    var useHttps by rememberSaveable { mutableStateOf(existing?.useHttps ?: false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    val portNumber = port.toIntOrNull()
    val valid = name.isNotBlank() && host.isNotBlank() && portNumber != null && portNumber in 1..65535 && username.isNotBlank()

    Column(
        modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RouterCover(size = 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                ProtocolBadge(factory.protocol)
            }
        }

        if (factory.protocol == Protocol.TELNET) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                    Text(
                        stringResource(R.string.telnet_warning),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                host, { host = it.trim() },
                label = { Text(stringResource(R.string.field_address)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(0.68f),
            )
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(R.string.field_port)) },
                singleLine = true,
                isError = portNumber == null || portNumber !in 1..65535,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.32f),
            )
        }
        val logsIn = factory.id != BasicFactory.ID
        if (!logsIn) {
            Text(stringResource(R.string.basic_no_login), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (logsIn) OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.field_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (logsIn) OutlinedTextField(
            password, { password = it },
            label = { Text(if (existing != null) stringResource(R.string.field_new_password) else stringResource(R.string.field_admin_password)) },
            supportingText = {
                Text(stringResource(if (existing != null) R.string.password_keep_hint else R.string.field_admin_password_hint))
            },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) stringResource(R.string.hide_password) else stringResource(R.string.show_password),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (logsIn) TextButton(onClick = { showHelp = true }) { Text(stringResource(R.string.login_help_button)) }
        if (factory.protocol == Protocol.WEB) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.use_https), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = useHttps, onCheckedChange = {
                    useHttps = it
                    if (port == "80" && it) port = "443" else if (port == "443" && !it) port = "80"
                })
            }
        }
        if (logsIn) Text(
            stringResource(R.string.password_encryption_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            enabled = valid,
            onClick = {
                val profile = if (existing == null) {
                    RouterProfile(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        firmwareId = factory.id,
                        protocol = factory.protocol,
                        host = host,
                        port = portNumber!!,
                        username = username.trim(),
                        password = password,
                        useHttps = useHttps,
                    )
                } else {
                    val sameEndpoint = existing.host == host && existing.port == portNumber
                    existing.copy(
                        name = name.trim(),
                        host = host,
                        port = portNumber!!,
                        username = username.trim(),
                        password = password.ifEmpty { existing.password },
                        passwordNeeded = existing.passwordNeeded && password.isEmpty(),
                        useHttps = useHttps,
                        // A pinned SSH key belongs to the old address; a new address must be confirmed again.
                        hostKeyFingerprint = existing.hostKeyFingerprint.takeIf { sameEndpoint },
                    )
                }
                onSave(profile)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null)
            Text(stringResource(R.string.save), modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (showHelp) LoginHelpDialog(factory.vendor, onDismiss = { showHelp = false })
}

private const val DEFAULT_ADDRESS = "192.168.1.1"
