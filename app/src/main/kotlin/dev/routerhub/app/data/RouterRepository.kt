package dev.routerhub.app.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dev.routerhub.api.ConnectionMedium
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

    // Writes come from screens and from the background new-device check, so each read-modify-write
    // happens under one lock, and nobody's change is lost to another's stale copy.
    @Synchronized
    fun upsert(profile: RouterProfile) {
        val current = _profiles.value
        save(
            if (current.any { it.id == profile.id }) current.map { if (it.id == profile.id) profile else it }
            else current + profile,
        )
    }

    /** Changes one router, starting from what is saved now. */
    @Synchronized
    fun update(id: String, change: (RouterProfile) -> RouterProfile) {
        val profile = get(id) ?: return
        val changed = change(profile)
        if (changed != profile) upsert(changed)
    }

    /** Changes the devices remembered for a router. */
    fun updateDevices(id: String, change: (List<KnownDevice>) -> List<KnownDevice>) =
        update(id) { it.copy(devices = change(it.devices)) }

    @Synchronized
    fun delete(id: String) = save(_profiles.value.filterNot { it.id == id })

    /** Adds the routers not saved yet; returns how many that was. */
    @Synchronized
    fun addImported(imported: List<RouterProfile>): Int {
        val added = ProfileTransfer.newOnes(imported, _profiles.value)
        if (added.isNotEmpty()) save(_profiles.value + added)
        return added.size
    }

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
        .put("passwordNeeded", p.passwordNeeded)
        .put("devices", JSONArray(p.devices.map(::deviceToJson)))
        .put("alertNewDevices", p.alertNewDevices)

    private fun deviceToJson(d: KnownDevice) = JSONObject()
        .put("mac", d.mac)
        .put("alias", d.alias ?: JSONObject.NULL)
        .put("known", d.known)
        .put("hostname", d.hostname ?: JSONObject.NULL)
        .put("ip", d.ipAddress ?: JSONObject.NULL)
        .put("medium", d.medium.name)
        .put("lastSeen", d.lastSeen ?: JSONObject.NULL)
        // Pairs of start and end, to keep the file small with 200 devices of 20 visits each.
        .put("visits", JSONArray(d.visits.flatMap { listOf(it.start, it.end) }))

    private fun deviceFromJson(o: JSONObject) = KnownDevice(
        mac = o.getString("mac"),
        alias = o.optString("alias").takeIf { it.isNotEmpty() && !o.isNull("alias") },
        known = o.optBoolean("known"),
        hostname = o.optString("hostname").takeIf { it.isNotEmpty() && !o.isNull("hostname") },
        ipAddress = o.optString("ip").takeIf { it.isNotEmpty() && !o.isNull("ip") },
        medium = ConnectionMedium.entries.firstOrNull { it.name == o.optString("medium") } ?: ConnectionMedium.UNKNOWN,
        lastSeen = if (o.isNull("lastSeen")) null else o.optLong("lastSeen"),
        visits = o.optJSONArray("visits")?.let { array ->
            (0 until array.length() / 2).map { Visit(array.getLong(2 * it), array.getLong(2 * it + 1)) }
        }.orEmpty(),
    )

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
        passwordNeeded = o.optBoolean("passwordNeeded"),
        alertNewDevices = o.optBoolean("alertNewDevices"),
        devices = o.optJSONArray("devices")?.let { array -> (0 until array.length()).map { deviceFromJson(array.getJSONObject(it)) } }.orEmpty(),
    )

    private companion object {
        const val KEY = "profiles"
        const val TAG = "RouterRepository"
    }
}
