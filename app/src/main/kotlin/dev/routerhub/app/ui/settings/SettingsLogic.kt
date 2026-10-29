package dev.routerhub.app.ui.settings

import dev.routerhub.api.SettingField
import dev.routerhub.api.SettingsPage
import dev.routerhub.api.TableCodec

/**
 * What the user actually changed: edited values that differ from what the page showed. Only
 * these are sent, so untouched fields (and secrets the app never saw) stay as the router has them.
 */
fun pendingChanges(page: SettingsPage, edits: Map<String, String>): Map<String, String> = buildMap {
    for (field in page.fields) {
        val edited = edits[field.key] ?: continue
        when (field) {
            is SettingField.Info -> Unit
            is SettingField.Secret -> if (edited.isNotEmpty()) put(field.key, edited)
            is SettingField.Toggle -> if (edited.toBoolean() != field.value) put(field.key, edited)
            is SettingField.Text -> if (edited != field.value) put(field.key, edited)
            is SettingField.Number -> if (edited.trim() != field.value.toString()) put(field.key, edited.trim())
            is SettingField.Choice -> if (edited != field.value) put(field.key, edited)
            is SettingField.Table -> if (edited != TableCodec.encode(field.rows)) put(field.key, edited)
        }
    }
}

/** Value to show for a field: the user's edit if any, otherwise the router's current one. */
fun currentValue(field: SettingField, edits: Map<String, String>): String = edits[field.key] ?: when (field) {
    is SettingField.Info -> field.value
    is SettingField.Secret -> ""
    is SettingField.Toggle -> field.value.toString()
    is SettingField.Text -> field.value
    is SettingField.Number -> field.value.toString()
    is SettingField.Choice -> field.value
    is SettingField.Table -> TableCodec.encode(field.rows)
}

/** Client-side hint shown while typing. The extension re-validates everything before sending. */
fun localError(field: SettingField, value: String): String? = when (field) {
    is SettingField.Number -> value.trim().toLongOrNull().let { n ->
        if (n == null || n < field.min || n > field.max) "Enter a number from ${field.min} to ${field.max}" else null
    }
    is SettingField.Text -> if (field.maxLength != null && value.length > field.maxLength!!) "Too long" else null
    is SettingField.Secret -> when {
        value.isEmpty() -> null
        value.length < field.minLength -> "At least ${field.minLength} characters"
        field.maxLength != null && value.length > field.maxLength!! -> "At most ${field.maxLength} characters"
        else -> null
    }
    is SettingField.Table -> {
        val max = field.maxRows
        if (max != null && TableCodec.decode(value).size > max) "At most $max ${field.itemName}s" else null
    }
    else -> null
}
