package dev.routerhub.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.routerhub.api.SettingField
import dev.routerhub.app.AppContainer
import dev.routerhub.app.ui.components.EmptyScreen

/**
 * Renders one extension-described settings page as a form. It knows nothing about any router:
 * every title, rule and warning comes from the extension's [dev.routerhub.api.SettingsPage].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageScreen(container: AppContainer, profileId: String, pageId: String, onBack: () -> Unit) {
    val viewModel: SettingsPageViewModel = viewModel(
        key = "settings/$profileId/$pageId",
        factory = SettingsPageViewModel.factory(profileId, pageId, container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirming by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val page = state.page
    val changes = if (page != null) pendingChanges(page, state.edits) else emptyMap()
    val invalid = page != null && page.fields.any { localError(it, currentValue(it, state.edits)) != null }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(page?.info?.title ?: "Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            onClick = { if (page?.applyWarning != null) confirming = true else viewModel.save() },
                            enabled = changes.isNotEmpty() && !invalid,
                        ) { Text("Save") }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when {
            state.loading && page == null -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            page == null -> EmptyScreen(state.loadError ?: "Couldn't load this page.", modifier) {
                TextButton(onClick = viewModel::load) { Text("Retry") }
            }
            else -> Column(modifier.verticalScroll(rememberScrollState()).imePadding().padding(bottom = 24.dp)) {
                page.info.summary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                page.fields.forEach { field ->
                    val value = currentValue(field, state.edits)
                    val error = state.fieldErrors[field.key] ?: localError(field, value)
                    FieldRow(
                        field = field,
                        value = value,
                        confirmation = state.confirmations[field.key].orEmpty(),
                        error = error,
                        enabled = !state.saving,
                        onChange = { viewModel.edit(field.key, it) },
                        onConfirmationChange = { viewModel.confirm(field.key, it) },
                    )
                }
            }
        }
    }

    if (confirming && page != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Apply changes?") },
            text = {
                Column {
                    page.applyWarning?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        "\nChanging: " + page.fields.filter { it.key in changes }.joinToString { it.title },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { confirming = false; viewModel.save() }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FieldRow(
    field: SettingField,
    value: String,
    confirmation: String,
    error: String?,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
) {
    val fieldModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    when (field) {
        is SettingField.Info -> ListItem(
            colors = transparent,
            headlineContent = { Text(field.title) },
            supportingContent = { Text(field.value + field.summary?.let { "\n$it" }.orEmpty()) },
        )

        is SettingField.Toggle -> ListItem(
            colors = transparent,
            headlineContent = { Text(field.title) },
            supportingContent = (error ?: field.summary)?.let { { FieldMessage(it, isError = error != null) } },
            trailingContent = { Switch(checked = value.toBoolean(), onCheckedChange = { onChange(it.toString()) }, enabled = enabled) },
            modifier = Modifier.clickable(enabled = enabled) { onChange((!value.toBoolean()).toString()) },
        )

        is SettingField.Text -> OutlinedTextField(
            value = value,
            onValueChange = { onChange(field.maxLength?.let { max -> it.take(max) } ?: it) },
            label = { Text(field.title) },
            singleLine = true,
            enabled = enabled,
            isError = error != null,
            supportingText = (error ?: field.summary)?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (field.keyboard == SettingField.Keyboard.TEXT) KeyboardType.Text else KeyboardType.Uri,
            ),
            modifier = fieldModifier,
        )

        is SettingField.Secret -> {
            var visible by remember { mutableStateOf(false) }
            val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(field.title) },
                singleLine = true,
                enabled = enabled,
                isError = error != null,
                supportingText = (error ?: field.summary)?.let { { Text(it) } },
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (visible) "Hide" else "Show",
                        )
                    }
                },
                modifier = fieldModifier,
            )
            if (field.requiresConfirmation && value.isNotEmpty()) {
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = onConfirmationChange,
                    label = { Text("Confirm ${field.title.lowercase()}") },
                    singleLine = true,
                    enabled = enabled,
                    isError = confirmation.isNotEmpty() && confirmation != value,
                    supportingText = if (confirmation.isNotEmpty() && confirmation != value) {
                        { Text("The two entries don't match") }
                    } else {
                        null
                    },
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = fieldModifier,
                )
            }
        }

        is SettingField.Number -> OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.filter(Char::isDigit).take(12)) },
            label = { Text(field.title) },
            singleLine = true,
            enabled = enabled,
            isError = error != null,
            supportingText = { Text(error ?: field.summary ?: "${field.min} to ${field.max}") },
            suffix = field.unit?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = fieldModifier,
        )

        is SettingField.Table -> TableEditor(field, value, error, enabled, onChange)

        is SettingField.Choice -> {
            var open by remember { mutableStateOf(false) }
            Box {
                ListItem(
                    colors = transparent,
                    headlineContent = { Text(field.title) },
                    supportingContent = {
                        Column {
                            Text(field.options.firstOrNull { it.value == value }?.label ?: value)
                            if (error != null) FieldMessage(error, isError = true)
                        }
                    },
                    trailingContent = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.clickable(enabled = enabled) { open = true },
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    field.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { open = false; onChange(option.value) },
                            trailingIcon = if (option.value == value) {
                                { Icon(Icons.Outlined.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A field's explanation, or the reason the extension or router refused it. A toggle or a choice has
 * no text field to hold an error, so without this a refused change would give no feedback at all.
 */
@Composable
private fun FieldMessage(text: String, isError: Boolean) {
    Text(text, color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified)
}
