package dev.routerhub.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.routerhub.api.ColumnKind
import dev.routerhub.api.SettingField
import dev.routerhub.api.TableCodec
import dev.routerhub.api.TableColumn

/**
 * An editable list. Rows can be deleted and new ones added; the router-specific work of turning
 * that into add and remove requests happens in the extension when the page is saved.
 */
@Composable
fun TableEditor(
    field: SettingField.Table,
    encodedRows: String,
    error: String?,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    val rows = TableCodec.decode(encodedRows)
    var adding by remember { mutableStateOf(false) }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    val canAdd = enabled && !field.readOnly && (field.maxRows == null || rows.size < field.maxRows!!) && field.columns.any { !it.readOnly }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(field.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                field.summary?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!field.readOnly) {
                TextButton(onClick = { adding = true }, enabled = canAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Add", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (rows.isEmpty()) {
            Text(
                field.emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        rows.forEachIndexed { index, row ->
            ListItem(
                colors = transparent,
                headlineContent = { Text(row.firstOrNull().orEmpty().ifEmpty { "-" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = rowDetails(field, row).takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
                trailingContent = if (field.readOnly) {
                    null
                } else {
                    {
                        IconButton(
                            onClick = { onChange(TableCodec.encode(rows.filterIndexed { i, _ -> i != index })) },
                            enabled = enabled,
                        ) { Icon(Icons.Outlined.Delete, contentDescription = "Remove ${field.itemName}") }
                    }
                },
            )
        }
    }

    if (adding) {
        AddRowDialog(
            field = field,
            onDismiss = { adding = false },
            onAdd = { row ->
                adding = false
                onChange(TableCodec.encode(rows + listOf(row)))
            },
        )
    }
}

/** The cells after the first, labelled by their column; choices show their label, not their value. */
private fun rowDetails(field: SettingField.Table, row: List<String>): String =
    field.columns.drop(1).zip(row.drop(1))
        .filter { (column, value) -> value.isNotEmpty() && column.kind != ColumnKind.SECRET }
        .joinToString(" · ") { (column, value) ->
            val shown = if (column.kind == ColumnKind.CHOICE) column.options.firstOrNull { it.value == value }?.label ?: value else value
            val label = column.shortTitle ?: column.title
            if (label.isEmpty()) shown else "$label: $shown"
        }

@Composable
private fun AddRowDialog(field: SettingField.Table, onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
    val asked = field.columns.filter { !it.readOnly }
    val firstTyped = asked.firstOrNull { it.kind != ColumnKind.CHOICE }?.key
    val values = remember {
        mutableStateMapOf<String, String>().apply {
            asked.forEach { put(it.key, it.defaultValue.ifEmpty { if (it.kind == ColumnKind.CHOICE) it.options.firstOrNull()?.value.orEmpty() else "" }) }
        }
    }
    // A required value may not be blank. Whether it is VALID is for the extension to say when saving.
    val complete = asked.all { it.optional || (values[it.key].orEmpty().isNotBlank()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${field.itemName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                asked.forEach { column ->
                    ColumnField(column, values[column.key].orEmpty(), autoFocus = column.key == firstTyped) { values[column.key] = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(field.columns.map { if (it.readOnly) "" else values[it.key].orEmpty().trim() }) },
                enabled = complete,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ColumnField(column: TableColumn, value: String, autoFocus: Boolean, onChange: (String) -> Unit) {
    val label = column.title + if (column.optional) " (optional)" else ""
    if (column.kind == ColumnKind.CHOICE) {
        var open by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth()) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(label) },
                supportingContent = { Text(column.options.firstOrNull { it.value == value }?.label ?: value.ifEmpty { "-" }) },
                trailingContent = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
                modifier = Modifier.clickable { open = true },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                column.options.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { open = false; onChange(option.value) })
                }
            }
        }
        return
    }
    var visible by remember { mutableStateOf(false) }
    val secret = column.kind == ColumnKind.SECRET
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        // The example only shows in a focused field, so focus the first one as the dialog opens.
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (column.kind == ColumnKind.NUMBER) it.filter(Char::isDigit).take(9) else it) },
        label = { Text(label) },
        placeholder = exampleFor(column.kind)?.let { { Text(it) } },
        singleLine = true,
        supportingText = column.hint?.let { { Text(it) } },
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (visible) "Hide" else "Show",
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = when (column.kind) {
                ColumnKind.NUMBER -> KeyboardType.Number
                ColumnKind.IP_ADDRESS -> KeyboardType.Uri
                ColumnKind.SECRET -> KeyboardType.Password
                else -> KeyboardType.Text
            },
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).focusRequester(focusRequester),
    )
}

/** What a value of this kind looks like, shown inside the empty field. Nothing for kinds with no fixed shape. */
private fun exampleFor(kind: ColumnKind): String? = when (kind) {
    ColumnKind.MAC_ADDRESS -> "e.g. 00:11:22:33:44:55"
    ColumnKind.IP_ADDRESS -> "e.g. 192.168.1.10"
    else -> null
}
