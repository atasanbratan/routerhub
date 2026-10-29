package dev.routerhub.api

// Binary-compatibility rule for every model in this file: only ever APPEND properties, and always
// give them a default value. @JvmOverloads keeps the old constructor signatures in the bytecode, so
// extensions compiled against an older router-api keep linking against a newer host.

public data class RouterStatus @JvmOverloads constructor(
    public val model: String? = null,
    public val firmwareVersion: String? = null,
    /**
     * Plain seconds instead of kotlin.time.Duration: inline value classes get name-mangled JVM
     * signatures, which makes a cross-APK binary contract fragile.
     */
    public val uptimeSeconds: Long? = null,
    public val wanIpv4: String? = null,
    public val wanConnected: Boolean? = null,
    public val wifiRadios: List<WifiRadio> = emptyList(),
    /** Firmware-specific facts the UI shows verbatim (label to value). */
    public val extras: Map<String, String> = emptyMap(),
)

public data class WifiRadio @JvmOverloads constructor(
    /** Opaque id passed back to [RouterFirmware.setWifiEnabled]. */
    public val id: String,
    public val band: WifiBand,
    public val enabled: Boolean,
    public val ssid: String? = null,
)

public enum class WifiBand { GHZ_2_4, GHZ_5, GHZ_6, UNKNOWN }

public data class DslStats @JvmOverloads constructor(
    public val lineState: DslLineState,
    public val downstreamKbps: Int,
    public val upstreamKbps: Int,
    public val attainableDownstreamKbps: Int? = null,
    public val attainableUpstreamKbps: Int? = null,
    public val snrMarginDownDb: Double? = null,
    public val snrMarginUpDb: Double? = null,
    public val attenuationDownDb: Double? = null,
    public val attenuationUpDb: Double? = null,
    public val modulation: String? = null,
)

public enum class DslLineState { SHOWTIME, TRAINING, DOWN, UNKNOWN }

public data class ConnectedClient @JvmOverloads constructor(
    /** Lower case and colon-separated. The stable identity of a client. */
    public val macAddress: String,
    public val ipAddress: String? = null,
    public val hostname: String? = null,
    public val interfaceName: String? = null,
    public val medium: ConnectionMedium = ConnectionMedium.UNKNOWN,
)

public enum class ConnectionMedium { WIRED, WIRELESS, UNKNOWN }
