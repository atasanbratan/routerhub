package dev.routerhub.app.data

import dev.routerhub.api.ConnectedClient
import dev.routerhub.api.ConnectionMedium

/** What this phone remembers about one device of a router. Nothing here is stored on the router. */
data class KnownDevice(
    /** Lower case, as in [ConnectedClient.macAddress]. */
    val mac: String,
    /** The name the user gave it. */
    val alias: String? = null,
    /** The user recognises this device. */
    val known: Boolean = false,
    val hostname: String? = null,
    val ipAddress: String? = null,
    val medium: ConnectionMedium = ConnectionMedium.UNKNOWN,
    /** When it was last seen connected, in milliseconds since the epoch. */
    val lastSeen: Long? = null,
)

enum class DeviceState { ONLINE, BLOCKED, SEEN }

/** One row of the Clients tab. */
data class DeviceRow(val client: ConnectedClient, val device: KnownDevice?, val state: DeviceState) {
    val title: String get() = device?.alias ?: client.hostname ?: client.macAddress
}

object DeviceBook {
    /** The oldest devices nobody named are dropped past this, so the list can't grow forever. */
    private const val MAX_DEVICES = 200

    /** [devices] after seeing [clients] at [now]. */
    fun record(devices: List<KnownDevice>, clients: List<ConnectedClient>, now: Long): List<KnownDevice> {
        val book = devices.associateByTo(LinkedHashMap()) { it.mac }
        clients.filter { it.online }.forEach { client ->
            val device = book[client.macAddress] ?: KnownDevice(client.macAddress)
            book[client.macAddress] = device.copy(
                hostname = client.hostname ?: device.hostname,
                ipAddress = client.ipAddress ?: device.ipAddress,
                medium = client.medium,
                lastSeen = now,
            )
        }
        val (kept, plain) = book.values.partition { it.alias != null || it.known }
        return kept + plain.sortedByDescending { it.lastSeen ?: 0 }.take((MAX_DEVICES - kept.size).coerceAtLeast(0))
    }

    /** Sets one device, adding it if it wasn't remembered yet. */
    fun update(devices: List<KnownDevice>, mac: String, change: (KnownDevice) -> KnownDevice): List<KnownDevice> {
        val updated = change(devices.firstOrNull { it.mac == mac } ?: KnownDevice(mac))
        return devices.filterNot { it.mac == mac } + updated
    }

    /** Online first, then blocked devices that are away, then everything else seen before, latest first. */
    fun rows(clients: List<ConnectedClient>, devices: List<KnownDevice>): List<DeviceRow> {
        val book = devices.associateBy { it.mac }
        val live = clients.map { client ->
            DeviceRow(client, book[client.macAddress], if (client.online) DeviceState.ONLINE else DeviceState.BLOCKED)
        }
        val present = clients.mapTo(HashSet()) { it.macAddress }
        val seen = devices.filter { it.mac !in present && it.lastSeen != null }.sortedByDescending { it.lastSeen }.map { device ->
            val client = ConnectedClient(device.mac, device.ipAddress, device.hostname, null, device.medium, false, false)
            DeviceRow(client, device, DeviceState.SEEN)
        }
        return live.sortedBy { it.state } + seen
    }
}
