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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.routerhub.api.Protocol
import dev.routerhub.api.RouterFirmwareFactory
import dev.routerhub.app.AppContainer
import dev.routerhub.app.data.RouterProfile
import dev.routerhub.app.ui.components.EmptyScreen
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            editProfileId != null -> "Edit router"
                            factory == null -> "Choose firmware"
                            else -> "Add router"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (factory != null && canChangeFirmware) firmwareId = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when {
            editProfileId != null && editing == null -> EmptyScreen("This router no longer exists.", modifier)
            editing != null && factory == null -> EmptyScreen(
                "The extension for this router isn't installed, so it can't be edited right now.",
                modifier,
            )
            factory == null -> FirmwarePicker(firmwares.values.toList(), onPick = { firmwareId = it.id }, modifier = modifier)
            else -> ConnectionForm(
                factory = factory,
                existing = editing,
                modifier = modifier,
                onSave = { profile ->
                    container.routers.upsert(profile)
                    onSaved(profile.id)
                },
            )
        }
    }
}

@Composable
private fun FirmwarePicker(factories: List<RouterFirmwareFactory>, onPick: (RouterFirmwareFactory) -> Unit, modifier: Modifier) {
    if (factories.isEmpty()) {
        EmptyScreen("No firmwares available.\nInstall a router extension first.", modifier)
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
    modifier: Modifier,
    onSave: (RouterProfile) -> Unit,
) {
    val title = if (factory.displayName.startsWith(factory.vendor)) factory.displayName else "${factory.vendor} ${factory.displayName}"
    var name by rememberSaveable { mutableStateOf(existing?.name ?: title) }
    var host by rememberSaveable { mutableStateOf(existing?.host ?: "192.168.1.1") }
    var port by rememberSaveable { mutableStateOf((existing?.port ?: factory.defaultPort).toString()) }
    var username by rememberSaveable {
        mutableStateOf(existing?.username ?: if (factory.protocol == Protocol.SSH) "root" else "admin")
    }
    // When editing, the stored password is never put back on screen: blank means "keep it".
    var password by rememberSaveable { mutableStateOf("") }
    var useHttps by rememberSaveable { mutableStateOf(existing?.useHttps ?: false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }

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
                        "Telnet sends your password unencrypted over the local network.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                host, { host = it.trim() },
                label = { Text("Address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(0.68f),
            )
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit).take(5) },
                label = { Text("Port") },
                singleLine = true,
                isError = portNumber == null || portNumber !in 1..65535,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.32f),
            )
        }
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password, { password = it },
            label = { Text(if (existing != null) "New password" else "Password") },
            supportingText = if (existing != null) {
                { Text("Leave blank to keep the current password") }
            } else {
                null
            },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (factory.protocol == Protocol.WEB) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Use HTTPS", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = useHttps, onCheckedChange = {
                    useHttps = it
                    if (port == "80" && it) port = "443" else if (port == "443" && !it) port = "80"
                })
            }
        }
        Text(
            "Passwords are encrypted on this device with a hardware-backed key.",
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
            Text("Save", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
