package dev.routerhub.app.data

import dev.routerhub.api.ConnectionConfig
import dev.routerhub.api.Protocol
import dev.routerhub.api.SshAuth

/** A router the user saved: which firmware drives it, and how to reach it. */
data class RouterProfile(
    val id: String,
    val name: String,
    /** [dev.routerhub.api.RouterFirmwareFactory.id]; resolves only while its extension is installed. */
    val firmwareId: String,
    val protocol: Protocol,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val useHttps: Boolean = false,
    /** Pinned SSH host key, set once the user confirmed it (trust on first use). */
    val hostKeyFingerprint: String? = null,
    /** Imported from a file, which holds no passwords: ask before the first connection. */
    val passwordNeeded: Boolean = false,
    val devices: List<KnownDevice> = emptyList(),
    /** Check for new devices in the background and notify about them. */
    val alertNewDevices: Boolean = false,
) {
    override fun toString() = "RouterProfile(id=$id, name=$name, firmware=$firmwareId, $protocol $host:$port)"
}

fun RouterProfile.toConnectionConfig(): ConnectionConfig = when (protocol) {
    Protocol.WEB -> ConnectionConfig.Web(host, username, password, port, useHttps)
    Protocol.SSH -> ConnectionConfig.Ssh(host, username, SshAuth.Password(password), port, hostKeyFingerprint)
    Protocol.TELNET -> ConnectionConfig.Telnet(host, username, password, port)
}
