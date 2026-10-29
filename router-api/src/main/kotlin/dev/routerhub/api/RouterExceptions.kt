package dev.routerhub.api

/**
 * Root of every failure an extension may surface. The host maps each subtype to a specific UI
 * reaction; any other throwable is treated as a bug in the extension.
 *
 * Open rather than sealed so extensions can add firmware-specific subtypes.
 */
public open class RouterException @JvmOverloads public constructor(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Username or password rejected. UI: ask for credentials again. */
public open class WrongCredentialsException @JvmOverloads public constructor(
    message: String = "Wrong username or password",
    cause: Throwable? = null,
) : RouterException(message, cause)

/**
 * The SSH server rejected the password or key. Extends [WrongCredentialsException] so the UI can
 * handle a bad login identically for both protocols.
 */
public class SshAuthenticationException @JvmOverloads public constructor(
    message: String = "SSH authentication failed",
    cause: Throwable? = null,
) : WrongCredentialsException(message, cause)

/**
 * A previously valid session is gone: cookie expired, router rebooted, SSH connection dropped.
 * The host calls [RouterFirmware.login] and retries the operation once.
 */
public class RouterSessionExpiredException @JvmOverloads public constructor(
    message: String = "Router session expired",
    cause: Throwable? = null,
) : RouterException(message, cause)

/** The firmware refuses because another administrator is logged in (common on single-session UIs). */
public class RouterBusyException @JvmOverloads public constructor(
    message: String = "Another administrator is logged in",
    cause: Throwable? = null,
) : RouterException(message, cause)

/** The router accepted the connection but didn't answer in time. */
public class FirmwareTimeoutException @JvmOverloads public constructor(
    message: String = "Router did not respond in time",
    cause: Throwable? = null,
) : RouterException(message, cause)

/** No route, connection refused, DNS failure... Usually the phone isn't on the router's LAN. */
public class RouterUnreachableException @JvmOverloads public constructor(
    message: String = "Router is unreachable",
    cause: Throwable? = null,
) : RouterException(message, cause)

/** The router answered, but not in the expected shape. Typically a new firmware revision. */
public class FirmwareParseException @JvmOverloads public constructor(
    message: String,
    cause: Throwable? = null,
) : RouterException(message, cause)

public class CapabilityNotSupportedException(
    public val capability: Capability,
) : RouterException("This firmware does not support $capability")

/** The host passed a [ConnectionConfig] the firmware can't use (e.g. SSH config to a web scraper). */
public class InvalidConfigurationException(message: String) : RouterException(message)

/**
 * A CLI command ran but reported failure. [exitCode] is null for CLIs that don't expose one
 * (e.g. restricted telnet shells, where failure is recognised from the output).
 */
public open class CommandFailedException(
    public val command: String,
    public val exitCode: Int?,
    public val output: String,
) : RouterException(
    "Command failed" + (exitCode?.let { " with exit code $it" } ?: "") + ": ${output.take(200)}",
)

/** A shell command run over SSH exited non-zero. */
public class SshCommandFailedException(
    command: String,
    exitCode: Int,
    public val stderr: String,
) : CommandFailedException(command, exitCode, stderr)

/** The router's identity could not be verified. The host never retries these automatically. */
public abstract class HostIdentityException(message: String) : RouterException(message)

/** First connection to this host: ask the user to confirm [presentedFingerprint], then pin it. */
public class SshHostKeyUnknownException(
    public val host: String,
    public val presentedFingerprint: String,
) : HostIdentityException("Unknown SSH host key for $host: $presentedFingerprint")

/** The pinned host key changed. Either the router was reset/reflashed or someone is intercepting. */
public class SshHostKeyMismatchException(
    public val host: String,
    public val expectedFingerprint: String,
    public val presentedFingerprint: String,
) : HostIdentityException(
    "SSH host key for $host changed (expected $expectedFingerprint, got $presentedFingerprint)",
)
