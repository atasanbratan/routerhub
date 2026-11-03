package dev.routerhub.app.extension

import dev.routerhub.api.ChoiceOption
import dev.routerhub.api.ClientAction
import dev.routerhub.api.ExtensionContract
import dev.routerhub.api.RouterStatus
import dev.routerhub.api.SettingField
import dev.routerhub.api.SettingsPage
import dev.routerhub.api.SettingsPageInfo
import dev.routerhub.api.TableColumn
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

/**
 * An extension's translations into one language, applied to the text it shows. Keys and values a
 * router reads or writes are never touched, only titles, summaries and labels. See
 * [ExtensionContract] for where an extension keeps its translations.
 */
class ExtensionText(private val translations: Map<String, String>) {
    fun tr(text: String): String = translations[text] ?: text

    private fun trOrNull(text: String?): String? = text?.let(::tr)

    fun page(info: SettingsPageInfo): SettingsPageInfo = info.copy(title = tr(info.title), summary = trOrNull(info.summary))

    fun page(page: SettingsPage): SettingsPage =
        page.copy(info = page(page.info), fields = page.fields.map(::field), applyWarning = trOrNull(page.applyWarning))

    fun action(action: ClientAction): ClientAction =
        action.copy(title = tr(action.title), summary = trOrNull(action.summary), confirmation = trOrNull(action.confirmation))

    fun status(status: RouterStatus): RouterStatus =
        if (translations.isEmpty()) status else status.copy(extras = status.extras.mapKeys { tr(it.key) })

    private fun field(field: SettingField): SettingField = when (field) {
        is SettingField.Info -> field.copy(title = tr(field.title), summary = trOrNull(field.summary))
        is SettingField.Toggle -> field.copy(title = tr(field.title), summary = trOrNull(field.summary))
        is SettingField.Text -> field.copy(title = tr(field.title), summary = trOrNull(field.summary))
        is SettingField.Secret -> field.copy(title = tr(field.title), summary = trOrNull(field.summary))
        is SettingField.Number -> field.copy(title = tr(field.title), summary = trOrNull(field.summary), unit = trOrNull(field.unit))
        is SettingField.Choice -> field.copy(title = tr(field.title), summary = trOrNull(field.summary), options = field.options.map(::option))
        is SettingField.Table -> field.copy(
            title = tr(field.title),
            summary = trOrNull(field.summary),
            columns = field.columns.map(::column),
            itemName = tr(field.itemName),
            emptyText = tr(field.emptyText),
        )
    }

    private fun column(column: TableColumn): TableColumn = column.copy(
        title = tr(column.title),
        options = column.options.map(::option),
        hint = trOrNull(column.hint),
        shortTitle = trOrNull(column.shortTitle),
    )

    private fun option(option: ChoiceOption): ChoiceOption = option.copy(label = tr(option.label))

    companion object {
        val NONE = ExtensionText(emptyMap())

        /**
         * The translations shipped next to [owner], the extension's own class, for [locale]: the
         * region's file first (`pt-BR.json`), then the language's (`pt.json`).
         */
        fun load(owner: Class<*>, locale: Locale = Locale.getDefault()): ExtensionText {
            val language = when (locale.language) {
                // Java's old codes, which some Android versions still report.
                "in" -> "id"
                "iw" -> "he"
                else -> locale.language
            }
            if (language.isEmpty() || language == "en") return NONE
            val names = listOfNotNull(locale.country.takeIf { it.isNotEmpty() }?.let { "$language-$it" }, language)
            val translations = names.reversed().fold(emptyMap<String, String>()) { found, name ->
                found + read(owner, "${ExtensionContract.TRANSLATIONS_DIR}/$name.json")
            }
            return if (translations.isEmpty()) NONE else ExtensionText(translations)
        }

        private fun read(owner: Class<*>, path: String): Map<String, String> = try {
            owner.getResourceAsStream(path)?.use { parse(it.readBytes().decodeToString()) }.orEmpty()
        } catch (_: Exception) {
            // A broken translation file leaves the extension in English; it never stops it working.
            emptyMap()
        }

        fun parse(json: String): Map<String, String> = try {
            val obj = JSONObject(json)
            obj.keys().asSequence().mapNotNull { key ->
                obj.optString(key).takeIf { it.isNotBlank() }?.let { key to it }
            }.toMap()
        } catch (_: JSONException) {
            emptyMap()
        }
    }
}
