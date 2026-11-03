package dev.routerhub.app.data

import dev.routerhub.api.Protocol
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/** The file isn't a routers export this version can read. */
class InvalidRoutersFileException(message: String) : Exception(message)

/**
 * The file behind Export and Import routers. It holds what it takes to find a router again, and the
 * names the user gave its devices: never a password and never a pinned SSH host key. So it can't be
 * decrypted or leak a login, an imported router asks for its password, and SSH host keys are
 * confirmed again on the new phone. Devices the user didn't name or mark stay out of it.
 */
object ProfileTransfer {
    private const val FORMAT = "routerhub-routers"
    private const val VERSION = 1

    fun encode(profiles: List<RouterProfile>): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("routers", JSONArray(profiles.map(::toJson)))
        .toString(2)

    /** Reads an exported file into new profiles. Firmware ids stay as they are, installed or not. */
    fun decode(text: String): List<RouterProfile> = try {
        val root = JSONObject(text)
        if (root.optString("format") != FORMAT) throw InvalidRoutersFileException("Not a routers file")
        if (root.optInt("version") !in 1..VERSION) throw InvalidRoutersFileException("Unknown version")
        val routers = root.getJSONArray("routers")
        (0 until routers.length()).map { fromJson(routers.getJSONObject(it)) }
    } catch (e: JSONException) {
        throw InvalidRoutersFileException(e.message.orEmpty())
    } catch (e: IllegalArgumentException) {
        throw InvalidRoutersFileException(e.message.orEmpty())
    }

    /** The [imported] routers that aren't in [existing] already, nor earlier in [imported]. */
    fun newOnes(imported: List<RouterProfile>, existing: List<RouterProfile>): List<RouterProfile> {
        val seen = existing.mapTo(mutableSetOf(), ::identity)
        return imported.filter { seen.add(identity(it)) }
    }

    private fun identity(p: RouterProfile) = listOf(p.firmwareId, p.host, p.port, p.username)

    private fun toJson(p: RouterProfile) = JSONObject()
        .put("name", p.name)
        .put("firmwareId", p.firmwareId)
        .put("protocol", p.protocol.name)
        .put("host", p.host)
        .put("port", p.port)
        .put("username", p.username)
        .put("useHttps", p.useHttps)
        .put("devices", JSONArray(p.devices.filter { it.alias != null || it.known }.map(::deviceToJson)))

    private fun deviceToJson(d: KnownDevice) = JSONObject()
        .put("mac", d.mac)
        .put("alias", d.alias ?: JSONObject.NULL)
        .put("known", d.known)

    private fun deviceFromJson(o: JSONObject) = KnownDevice(
        mac = o.getString("mac").lowercase(),
        alias = o.optString("alias").takeIf { it.isNotEmpty() && !o.isNull("alias") },
        known = o.optBoolean("known"),
    )

    private fun fromJson(o: JSONObject): RouterProfile {
        val port = o.getInt("port")
        require(port in 1..65535) { "Bad port" }
        val texts = listOf("name", "firmwareId", "host", "username").map(o::getString)
        require(texts.none(String::isBlank)) { "Blank value" }
        val (name, firmwareId, host, username) = texts
        return RouterProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            firmwareId = firmwareId,
            protocol = Protocol.valueOf(o.getString("protocol")),
            host = host,
            port = port,
            username = username,
            password = "",
            useHttps = o.optBoolean("useHttps"),
            passwordNeeded = true,
            devices = o.optJSONArray("devices")?.let { array -> (0 until array.length()).map { deviceFromJson(array.getJSONObject(it)) } }.orEmpty(),
        )
    }
}
