package dev.routerhub.api

/**
 * Manifest-level contract between the host app and extension APKs.
 *
 * An extension APK declares in its AndroidManifest.xml:
 *  - `<uses-feature android:name="dev.routerhub.extension" />` so the host can find it;
 *  - `<meta-data android:name="dev.routerhub.extension.class" android:value=".MyExtension" />`
 *    naming a public no-arg class implementing [RouterExtension]. A leading dot is resolved
 *    against the package name, and several classes may be separated by `;`;
 *  - `<meta-data android:name="dev.routerhub.extension.api" android:value="1" />` holding the
 *    [API_VERSION] it was compiled against.
 */
public object ExtensionContract {
    public const val FEATURE: String = "dev.routerhub.extension"
    public const val META_ENTRY_CLASS: String = "dev.routerhub.extension.class"
    public const val META_API_VERSION: String = "dev.routerhub.extension.api"

    /**
     * Bump whenever extensions may start relying on something new in this module, so older hosts
     * refuse them instead of failing at runtime. v2 added settings pages and the TELNET protocol;
     * v3 added editable list fields (`SettingField.Table`).
     */
    public const val API_VERSION: Int = 3

    /** Oldest extension API version the current host can still load. */
    public const val MIN_SUPPORTED_API_VERSION: Int = 1
}
