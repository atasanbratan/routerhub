package dev.routerhub.app.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dev.routerhub.api.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Saved routers, persisted as JSON with passwords encrypted by [SecretCipher]. */
class RouterRepository(context: Context, private val cipher: SecretCipher) {

    private val prefs = context.getSharedPreferences("routers", Context.MODE_PRIVATE)
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<RouterProfile>> = _profiles.asStateFlow()

    fun get(id: String): RouterProfile? = _profiles.value.firstOrNull { it.id == id }

    fun upsert(profile: RouterProfile) {
        val current = _profiles.value
        save(
            if (current.any { it.id == profile.id }) current.map { if (it.id == profile.id) profile else it }
            else current + profile,
        )
    }

    fun delete(id: String) = save(_profiles.value.filterNot { it.id == id })

    private fun save(profiles: List<RouterProfile>) {
        _profiles.value = profiles
        prefs.edit { putString(KEY, JSONArray(profiles.map(::toJson)).toString()) }
    }

    private fun load(): List<RouterProfile> = try {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
    } catch (e: Exception) {
        Log.e(TAG, "Could not read saved routers", e)
        emptyList()
    }

    private fun toJson(p: RouterProfile) = JSONObject()
        .put("id", p.id)
        .put("name", p.name)
        .put("firmwareId", p.firmwareId)
        .put("protocol", p.protocol.name)
        .put("host", p.host)
        .put("port", p.port)
        .put("username", p.username)
        .put("password", cipher.encrypt(p.password))
        .put("useHttps", p.useHttps)
        .put("hostKey", p.hostKeyFingerprint ?: JSONObject.NULL)

    private fun fromJson(o: JSONObject) = RouterProfile(
        id = o.getString("id"),
        name = o.getString("name"),
        firmwareId = o.getString("firmwareId"),
        protocol = Protocol.valueOf(o.getString("protocol")),
        host = o.getString("host"),
        port = o.getInt("port"),
        username = o.getString("username"),
        // If the Keystore key is gone (e.g. data restored onto another device), the password can't
        // be recovered; the user re-enters it instead of losing the whole profile.
        password = runCatching { cipher.decrypt(o.getString("password")) }.getOrDefault(""),
        useHttps = o.optBoolean("useHttps"),
        hostKeyFingerprint = o.optString("hostKey").takeIf { it.isNotEmpty() && !o.isNull("hostKey") },
    )

    private companion object {
        const val KEY = "profiles"
        const val TAG = "RouterRepository"
    }
}
