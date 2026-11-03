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

/**
 * Titles of the fields in [changes] that [reloaded] doesn't show as sent, after a save. Case and
 * surrounding spaces don't count, as routers often normalise them. Secrets can't be read back, so
 * they aren't checked, nor are fields the reloaded page no longer has.
 */
fun notKept(reloaded: SettingsPage, changes: Map<String, String>): List<String> = reloaded.fields.mapNotNull { field ->
    val sent = changes[field.key] ?: return@mapNotNull null
    val kept = when (field) {
        is SettingField.Info, is SettingField.Secret -> true
        is SettingField.Toggle -> sent.toBoolean() == field.value
        is SettingField.Number -> sent.trim() == field.value.toString()
        is SettingField.Text -> same(sent, field.value)
        is SettingField.Choice -> same(sent, field.value)
        is SettingField.Table -> TableCodec.decode(sent).map { row -> row.map(::normal) } == field.rows.map { row -> row.map(::normal) }
    }
    field.title.takeUnless { kept }
}

private fun normal(value: String) = value.trim().lowercase()

private fun same(a: String, b: String) = normal(a) == normal(b)

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

/** True while a changed secret that must be typed twice doesn't have a matching second entry. */
fun unconfirmed(page: SettingsPage, changes: Map<String, String>, confirmations: Map<String, String>): Boolean =
    page.fields.filterIsInstance<SettingField.Secret>().any {
        it.requiresConfirmation && it.key in changes && confirmations[it.key] != changes[it.key]
    }

/** What [localError] found; the screen turns it into text. */
sealed interface LocalError {
    data class OutOfRange(val min: Long, val max: Long) : LocalError
    data object TooLong : LocalError
    data class TooShort(val minLength: Int) : LocalError
    data class OverLength(val maxLength: Int) : LocalError
    data class TooManyRows(val max: Int, val itemName: String) : LocalError
}

/** Client-side hint shown while typing. The extension re-validates everything before sending. */
fun localError(field: SettingField, value: String): LocalError? = when (field) {
    is SettingField.Number -> value.trim().toLongOrNull().let { n ->
        if (n == null || n < field.min || n > field.max) LocalError.OutOfRange(field.min, field.max) else null
    }
    is SettingField.Text -> if (field.maxLength != null && value.length > field.maxLength!!) LocalError.TooLong else null
    is SettingField.Secret -> when {
        value.isEmpty() -> null
        value.length < field.minLength -> LocalError.TooShort(field.minLength)
        field.maxLength != null && value.length > field.maxLength!! -> LocalError.OverLength(field.maxLength!!)
        else -> null
    }
    is SettingField.Table -> {
        val max = field.maxRows
        if (max != null && TableCodec.decode(value).size > max) LocalError.TooManyRows(max, field.itemName) else null
    }
    else -> null
}
