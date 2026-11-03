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
    /** When this app saw it connected, oldest first. Only covers the times the app looked. */
    val visits: List<Visit> = emptyList(),
)

/** A stretch of time this app kept seeing a device connected, in milliseconds since the epoch. */
data class Visit(val start: Long, val end: Long)

enum class DeviceState { ONLINE, BLOCKED, SEEN }

/** One row of the Clients tab. */
data class DeviceRow(val client: ConnectedClient, val device: KnownDevice?, val state: DeviceState) {
    val title: String get() = device?.alias ?: client.hostname ?: client.macAddress
}

object DeviceBook {
    /** The oldest devices nobody named are dropped past this, so the list can't grow forever. */
    private const val MAX_DEVICES = 200

    /** Longer than the 15 minutes between new-device checks, so a device seen at every check stays one visit. */
    const val VISIT_GAP_MILLIS = 20 * 60 * 1000L
    const val KEEP_VISITS_MILLIS = 14 * 24 * 60 * 60 * 1000L
    const val MAX_VISITS = 20

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
                visits = extendVisits(device.visits, now),
            )
        }
        val (kept, plain) = book.values.partition { it.alias != null || it.known }
        return kept + plain.sortedByDescending { it.lastSeen ?: 0 }.take((MAX_DEVICES - kept.size).coerceAtLeast(0))
    }

    /**
     * The online [clients] this phone has never seen on the router. Nothing is new while [devices] is
     * still empty: the first look at a router only learns what is already there.
     */
    fun newDevices(devices: List<KnownDevice>, clients: List<ConnectedClient>): List<ConnectedClient> {
        if (devices.isEmpty()) return emptyList()
        val known = devices.mapTo(HashSet()) { it.mac }
        return clients.filter { it.online && it.macAddress !in known }
    }

    /**
     * [visits] after seeing the device at [now]: the latest visit grows when it ended less than
     * [VISIT_GAP_MILLIS] ago, otherwise a new one starts. Old and surplus visits are dropped.
     */
    fun extendVisits(visits: List<Visit>, now: Long): List<Visit> {
        val last = visits.lastOrNull()
        val extended = if (last != null && now >= last.end && now - last.end <= VISIT_GAP_MILLIS) {
            visits.dropLast(1) + last.copy(end = now)
        } else {
            visits + Visit(now, now)
        }
        return extended.filter { now - it.end <= KEEP_VISITS_MILLIS }.takeLast(MAX_VISITS)
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
