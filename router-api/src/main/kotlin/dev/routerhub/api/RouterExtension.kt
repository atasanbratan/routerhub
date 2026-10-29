package dev.routerhub.api

/** Transport a firmware implementation speaks. Decides which [ConnectionConfig] it accepts. */
public enum class Protocol { WEB, SSH, TELNET }

/** Optional features. The UI only offers controls for what a firmware declares. */
public enum class Capability { STATUS, DSL_STATS, CONNECTED_CLIENTS, REBOOT, WIFI_TOGGLE, SETTINGS }

/**
 * Entry point of an extension APK, named by the `dev.routerhub.extension.class` meta-data.
 *
 * The host instantiates it once per process through its public no-arg constructor, so it must be
 * cheap and stateless: no I/O, no threads.
 */
public interface RouterExtension {
    public val factories: List<RouterFirmwareFactory>
}

/**
 * Describes one supported firmware family and opens sessions for it. A single extension may ship
 * several factories, e.g. one per hardware revision.
 */
public interface RouterFirmwareFactory {
    /** Globally unique and stable forever, because saved router profiles reference it. */
    public val id: String
    public val displayName: String
    public val vendor: String
    public val protocol: Protocol
    public val capabilities: Set<Capability>

    /** Pre-filled in the "add router" form. */
    public val defaultPort: Int
        get() = when (protocol) {
            Protocol.WEB -> 80
            Protocol.SSH -> 22
            Protocol.TELNET -> 23
        }

    /**
     * Creates a new, not yet authenticated session. Must not perform I/O.
     *
     * @throws InvalidConfigurationException if [config] doesn't match [protocol].
     */
    public fun create(config: ConnectionConfig): RouterFirmware
}
