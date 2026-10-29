package dev.routerhub.app.ui

import dev.routerhub.api.CapabilityNotSupportedException
import dev.routerhub.api.CommandFailedException
import dev.routerhub.api.FirmwareParseException
import dev.routerhub.api.FirmwareTimeoutException
import dev.routerhub.api.InvalidSettingException
import dev.routerhub.api.RouterBusyException
import dev.routerhub.api.RouterUnreachableException
import dev.routerhub.api.SshHostKeyMismatchException
import dev.routerhub.api.WrongCredentialsException
import dev.routerhub.app.engine.ExtensionFailureException
import java.util.Locale

fun formatUptime(seconds: Long): String {
    val days = seconds / 86_400
    val hours = seconds % 86_400 / 3_600
    val minutes = seconds % 3_600 / 60
    return buildList {
        if (days > 0) add("${days}d")
        if (days > 0 || hours > 0) add("${hours}h")
        add("${minutes}m")
    }.joinToString(" ")
}

fun formatKbps(kbps: Int): String =
    if (kbps >= 1000) String.format(Locale.US, "%.1f Mbps", kbps / 1000.0) else "$kbps kbps"

fun formatDb(value: Double): String = String.format(Locale.US, "%.1f dB", value)

/** One-line, user-facing explanation of a failed router operation. */
fun friendlyError(error: Throwable): String = when (error) {
    is WrongCredentialsException -> error.message ?: "Wrong username or password"
    is RouterUnreachableException -> "Can't reach the router. Is this device on its network?"
    is FirmwareTimeoutException -> "The router didn't answer in time"
    is RouterBusyException -> error.message ?: "The router is busy with another session"
    is SshHostKeyMismatchException -> "The router's SSH key changed. Refusing to connect"
    is FirmwareParseException -> "Unexpected answer from the router (${error.message})"
    is CapabilityNotSupportedException -> "Not supported by this firmware"
    is InvalidSettingException -> error.message ?: "That value isn't accepted"
    is CommandFailedException -> "The router rejected the change" + error.output.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
    is ExtensionFailureException -> error.message ?: "The extension failed"
    else -> error.message ?: error::class.java.simpleName
}
