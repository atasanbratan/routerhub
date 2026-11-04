package dev.routerhub.app.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", null)!!) }.getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean("dynamic_color", false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _pureBlack = MutableStateFlow(prefs.getBoolean("pure_black", false))
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()

    private val _appLock = MutableStateFlow(prefs.getBoolean("app_lock", false))
    val appLock: StateFlow<Boolean> = _appLock.asStateFlow()

    private val _autoUpdates = MutableStateFlow(prefs.getBoolean("auto_updates", true))

    /** Mihon's automatic update checks: the app on start, and a background check for app and extensions. */
    val autoUpdates: StateFlow<Boolean> = _autoUpdates.asStateFlow()

    /** When the app last asked GitHub for a new version on its own, in epoch milliseconds. */
    var lastAppUpdateCheck: Long
        get() = prefs.getLong("last_app_update_check", 0)
        set(value) = prefs.edit { putLong("last_app_update_check", value) }

    /** The last app version and extension updates a notification announced, so each is announced once. */
    var notifiedAppVersion: String?
        get() = prefs.getString("notified_app_version", null)
        set(value) = prefs.edit { putString("notified_app_version", value) }

    var notifiedExtensionUpdates: String?
        get() = prefs.getString("notified_extension_updates", null)
        set(value) = prefs.edit { putString("notified_extension_updates", value) }

    val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun setAutoUpdates(enabled: Boolean) {
        _autoUpdates.value = enabled
        prefs.edit { putBoolean("auto_updates", enabled) }
    }

    fun setPureBlack(enabled: Boolean) {
        _pureBlack.value = enabled
        prefs.edit { putBoolean("pure_black", enabled) }
    }

    fun setAppLock(enabled: Boolean) {
        _appLock.value = enabled
        prefs.edit { putBoolean("app_lock", enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit { putString("theme_mode", mode.name) }
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs.edit { putBoolean("dynamic_color", enabled) }
    }
}
