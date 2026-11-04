package dev.routerhub.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.routerhub.api.CapabilityNotSupportedException
import dev.routerhub.api.CommandFailedException
import dev.routerhub.api.FirmwareParseException
import dev.routerhub.api.FirmwareTimeoutException
import dev.routerhub.api.InvalidSettingException
import dev.routerhub.api.RouterBusyException
import dev.routerhub.api.RouterFirmwareFactory
import dev.routerhub.api.RouterUnreachableException
import dev.routerhub.api.SshHostKeyMismatchException
import dev.routerhub.api.WrongCredentialsException
import dev.routerhub.app.R
import dev.routerhub.app.basic.BasicFactory
import dev.routerhub.app.engine.ExtensionFailureException

@Composable
fun formatUptime(seconds: Long): String {
    val days = seconds / 86_400
    val hours = seconds % 86_400 / 3_600
    val minutes = seconds % 3_600 / 60
    return when {
        days > 0 -> stringResource(R.string.uptime_days, days, hours, minutes)
        hours > 0 -> stringResource(R.string.uptime_hours, hours, minutes)
        else -> stringResource(R.string.uptime_minutes, minutes)
    }
}

@Composable
fun formatKbps(kbps: Int): String =
    if (kbps >= 1000) stringResource(R.string.speed_mbps, kbps / 1000.0) else stringResource(R.string.speed_kbps, kbps)

@Composable
fun formatDb(value: Double): String = stringResource(R.string.decibels, value)

/** One-line, user-facing explanation of a failed router operation. */
fun friendlyError(context: Context, error: Throwable): String = when (error) {
    is WrongCredentialsException -> error.message ?: context.getString(R.string.error_credentials)
    is RouterUnreachableException -> context.getString(R.string.error_unreachable)
    is FirmwareTimeoutException -> context.getString(R.string.error_timeout)
    is RouterBusyException -> error.message ?: context.getString(R.string.error_busy)
    is SshHostKeyMismatchException -> context.getString(R.string.error_host_key_changed)
    is FirmwareParseException -> context.getString(R.string.error_parse, error.message)
    is CapabilityNotSupportedException -> context.getString(R.string.error_not_supported)
    is InvalidSettingException -> error.message ?: context.getString(R.string.error_value_refused)
    is CommandFailedException -> error.output.takeIf { it.isNotBlank() }?.let { context.getString(R.string.error_rejected_output, it) }
        ?: context.getString(R.string.error_rejected)
    is ExtensionFailureException -> error.message ?: context.getString(R.string.error_extension)
    else -> error.message ?: error::class.java.simpleName
}

/** The name of [factory], vendor first unless the name already starts with it. Basic mode is the app's own, so its name is translated. */
@Composable
fun firmwareTitle(factory: RouterFirmwareFactory): String = when {
    factory.id == BasicFactory.ID -> stringResource(R.string.basic_name)
    factory.displayName.startsWith(factory.vendor) -> factory.displayName
    else -> "${factory.vendor} ${factory.displayName}"
}

/** [RouterFirmwareFactory.vendor], translated for basic mode. */
@Composable
fun firmwareVendor(factory: RouterFirmwareFactory): String =
    if (factory.id == BasicFactory.ID) stringResource(R.string.basic_vendor) else factory.vendor
