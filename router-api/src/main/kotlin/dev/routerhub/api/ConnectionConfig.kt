package dev.routerhub.api

/**
 * Everything a firmware needs to reach a router. The host stores one per router profile
 * (encrypted at rest) and passes the variant that matches [RouterFirmwareFactory.protocol].
 *
 * Every variant redacts `toString()` so credentials never end up in logs or crash reports.
 */
public sealed class ConnectionConfig {
    public abstract val host: String
    public abstract val port: Int
    public abstract val username: String
    public abstract val timeoutMillis: Long
    public abstract val protocol: Protocol

    protected fun validate() {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65535) { "port out of range: $port" }
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
    }

    /** Credentials for an HTTP(S) admin web UI. */
    public data class Web @JvmOverloads constructor(
        override val host: String,
        override val username: String,
        public val password: String,
        override val port: Int = 80,
        public val useHttps: Boolean = false,
        override val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ) : ConnectionConfig() {
        override val protocol: Protocol get() = Protocol.WEB

        init {
            validate()
        }

        override fun toString(): String =
            "Web(host=$host, port=$port, username=$username, https=$useHttps, password=***)"
    }

    /** Credentials for a shell over SSH. */
    public data class Ssh @JvmOverloads constructor(
        override val host: String,
        override val username: String,
        public val auth: SshAuth,
        override val port: Int = 22,
        /**
         * Pinned server host key in OpenSSH format (`SHA256:<base64>`). It is `null` on the first
         * connection: the extension then throws [SshHostKeyUnknownException] carrying the key the
         * server presented, the host asks the user to confirm it, and retries with it pinned
         * (trust on first use, like `ssh` does with known_hosts).
         */
        public val hostKeyFingerprint: String? = null,
        override val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ) : ConnectionConfig() {
        override val protocol: Protocol get() = Protocol.SSH

        init {
            validate()
        }

        override fun toString(): String =
            "Ssh(host=$host, port=$port, username=$username, auth=$auth, hostKey=$hostKeyFingerprint)"
    }

    /**
     * Credentials for a telnet CLI, which many older routers expose next to their web UI.
     *
     * Telnet is unencrypted: the password crosses the LAN in clear text, and there is no host
     * identity to pin. Offer it only where SSH isn't available.
     */
    public data class Telnet @JvmOverloads constructor(
        override val host: String,
        override val username: String,
        public val password: String,
        override val port: Int = 23,
        override val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ) : ConnectionConfig() {
        override val protocol: Protocol get() = Protocol.TELNET

        init {
            validate()
        }

        override fun toString(): String = "Telnet(host=$host, port=$port, username=$username, password=***)"
    }

    public companion object {
        public const val DEFAULT_TIMEOUT_MILLIS: Long = 15_000
    }
}

public sealed class SshAuth {
    public data class Password(public val password: String) : SshAuth() {
        override fun toString(): String = "Password(***)"
    }

    /** @param privateKey PEM or OpenSSH-encoded private key. */
    public class PrivateKey @JvmOverloads constructor(
        public val privateKey: ByteArray,
        public val passphrase: String? = null,
    ) : SshAuth() {
        override fun toString(): String = "PrivateKey(***)"
    }
}
