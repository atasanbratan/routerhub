package dev.routerhub.api

/**
 * A live session with one router. Protocol-agnostic: the host never knows whether a call turns
 * into HTTP requests against a web UI or shell commands over SSH.
 *
 * Contract for implementers:
 *  - Every function is main-safe. Do blocking work on your own dispatcher (`Dispatchers.IO`).
 *  - Cooperate with cancellation. The host enforces timeouts by cancelling the calling coroutine,
 *    so blocking calls must be cancellable (OkHttp `Call.cancel()`, interruptible SSH reads...).
 *  - Only throw [RouterException] subtypes. Anything else is reported as a bug in the extension.
 *  - The host serialises calls on a session, so no internal locking is needed.
 *
 * Optional operations have default bodies that throw [CapabilityNotSupportedException]. Override
 * exactly the ones listed in [capabilities].
 */
public interface RouterFirmware {
    public val capabilities: Set<Capability>

    /**
     * Authenticates: form login, HTTP basic-auth probe, SSH handshake... The host calls it again
     * after a [RouterSessionExpiredException] and retries the failed operation once.
     */
    public suspend fun login()

    public suspend fun getStatus(): RouterStatus

    public suspend fun getDslStats(): DslStats = unsupported(Capability.DSL_STATS)

    public suspend fun getConnectedClients(): List<ConnectedClient> =
        unsupported(Capability.CONNECTED_CLIENTS)

    public suspend fun reboot(): Unit = unsupported(Capability.REBOOT)

    /**
     * Idempotent rather than a toggle, so a retried call can't flip the radio back.
     *
     * @param radioId one of the [RouterStatus.wifiRadios] ids, or `null` for every radio.
     */
    public suspend fun setWifiEnabled(radioId: String?, enabled: Boolean): Unit =
        unsupported(Capability.WIFI_TOGGLE)

    /** Configuration pages this firmware can read and write (see [SettingsPage]). */
    public suspend fun listSettingsPages(): List<SettingsPageInfo> = unsupported(Capability.SETTINGS)

    /** Current values of one page. */
    public suspend fun readSettings(pageId: String): SettingsPage = unsupported(Capability.SETTINGS)

    /**
     * Applies [changes] (field key to new value) to one page. Only changed fields are included;
     * a [SettingField.Secret] absent from the map keeps its current value.
     *
     * @throws InvalidSettingException if a value is rejected before contacting the router.
     */
    public suspend fun writeSettings(pageId: String, changes: Map<String, String>): Unit =
        unsupported(Capability.SETTINGS)

    /** Logs out where supported and releases sockets and threads. Idempotent; never throws. */
    public suspend fun close()
}

private fun unsupported(capability: Capability): Nothing =
    throw CapabilityNotSupportedException(capability)
