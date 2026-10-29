package dev.routerhub.api

/**
 * Declarative settings pages, the router equivalent of Tachiyomi's ConfigurableSource.
 *
 * An extension describes each page of its router's configuration as a list of typed fields. The
 * host renders them as a generic form and sends back only what the user changed. New pages
 * therefore need no host changes.
 *
 * Values travel as strings to keep this binary contract simple: "true"/"false" for toggles, the
 * option value for choices, decimal digits for numbers.
 */
public data class SettingsPageInfo @JvmOverloads constructor(
    /** Stable id, passed back to [RouterFirmware.readSettings] and [RouterFirmware.writeSettings]. */
    public val id: String,
    public val title: String,
    /** Groups pages in the host UI, e.g. "Wireless", "Network", "System". */
    public val group: String,
    public val summary: String? = null,
)

public data class SettingsPage @JvmOverloads constructor(
    public val info: SettingsPageInfo,
    public val fields: List<SettingField>,
    /** Shown before saving, e.g. "Saving restarts Wi-Fi; wireless devices reconnect." */
    public val applyWarning: String? = null,
    /**
     * Key of a [SettingField.Secret] that holds the router login password. When the user changes
     * it, the host also updates the password it stores for this router.
     */
    public val loginPasswordKey: String? = null,
)

public data class ChoiceOption(public val value: String, public val label: String)

public sealed class SettingField {
    public abstract val key: String
    public abstract val title: String
    public abstract val summary: String?

    /** Read-only value. */
    public data class Info @JvmOverloads constructor(
        override val key: String,
        override val title: String,
        public val value: String,
        override val summary: String? = null,
    ) : SettingField()

    public data class Toggle @JvmOverloads constructor(
        override val key: String,
        override val title: String,
        public val value: Boolean,
        override val summary: String? = null,
    ) : SettingField()

    public data class Text @JvmOverloads constructor(
        override val key: String,
        override val title: String,
        public val value: String,
        override val summary: String? = null,
        public val maxLength: Int? = null,
        public val keyboard: Keyboard = Keyboard.TEXT,
    ) : SettingField()

    /**
     * A password-like value. Its current value is never sent to the host: the field starts empty
     * and an empty submission means "keep the current one".
     */
    public data class Secret @JvmOverloads constructor(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        public val minLength: Int = 0,
        public val maxLength: Int? = null,
        /** True when the router requires a value on every save (no "keep current"). */
        public val required: Boolean = false,
        /** The host asks the user to type the value twice before submitting it. */
        public val requiresConfirmation: Boolean = false,
    ) : SettingField()

    public data class Number @JvmOverloads constructor(
        override val key: String,
        override val title: String,
        public val value: Long,
        public val min: Long,
        public val max: Long,
        override val summary: String? = null,
        public val unit: String? = null,
    ) : SettingField()

    public data class Choice @JvmOverloads constructor(
        override val key: String,
        override val title: String,
        public val value: String,
        public val options: List<ChoiceOption>,
        override val summary: String? = null,
    ) : SettingField()

    /**
     * An editable list: port forwards, filter entries, routes. Rows are lists of strings aligned
     * with [columns]. On submit the value is the COMPLETE new list encoded with [TableCodec]; the
     * extension compares it with what the router holds and issues the router's own add and remove
     * requests, so the host never needs to know how a given router edits its lists.
     */
    public data class Table @JvmOverloads constructor(
        override val key: String,
        override val title: String,
        public val columns: List<TableColumn>,
        public val rows: List<List<String>>,
        override val summary: String? = null,
        /** The router's limit, if it has one. */
        public val maxRows: Int? = null,
        /** What one row is called in the UI, e.g. "rule" or "address". */
        public val itemName: String = "entry",
        public val emptyText: String = "Nothing here yet",
        /** Shown but not editable, like a log. The host offers neither add nor delete. */
        public val readOnly: Boolean = false,
    ) : SettingField()

    public enum class Keyboard { TEXT, NUMBER, IP_ADDRESS }
}

/**
 * [SECRET] is a write-only column: the add form asks for it, masked, and a row never shows it. The
 * router doesn't reveal stored passwords either, so rows read back from it carry an empty cell there.
 */
public enum class ColumnKind { TEXT, NUMBER, CHOICE, IP_ADDRESS, MAC_ADDRESS, SECRET }

public data class TableColumn @JvmOverloads constructor(
    public val key: String,
    public val title: String,
    public val kind: ColumnKind = ColumnKind.TEXT,
    /** The choices of a [ColumnKind.CHOICE] column. */
    public val options: List<ChoiceOption> = emptyList(),
    public val hint: String? = null,
    /** Shown in the list but not asked for when adding a row; the router fills it in. */
    public val readOnly: Boolean = false,
    /** May stay empty when adding a row. */
    public val optional: Boolean = false,
    /** Preselected value in the add form. */
    public val defaultValue: String = "",
    /**
     * Label used in a row's one-line summary. `null` uses [title]; an empty string shows the bare
     * value, for columns that explain themselves (a protocol, an address).
     */
    public val shortTitle: String? = null,
)

/** A submitted value was rejected before anything was sent to the router. */
public class InvalidSettingException(
    public val key: String,
    message: String,
) : RouterException(message)
