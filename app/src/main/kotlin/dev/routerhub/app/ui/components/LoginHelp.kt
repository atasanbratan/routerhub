package dev.routerhub.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.routerhub.app.R

/** What people get stuck on when logging in. A factory reset comes last, as it costs them their network. */
@Composable
fun LoginHelpDialog(vendor: String?, onDismiss: () -> Unit) {
    val hint = when (vendor) {
        "OpenWrt" -> R.string.login_help_openwrt
        "Tenda" -> R.string.login_help_tenda
        "TP-Link" -> R.string.login_help_tplink
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_help_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.login_help_which))
                Text(stringResource(R.string.login_help_where))
                hint?.let { Text(stringResource(it)) }
                Text(stringResource(R.string.login_help_reset))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.login_help_close)) } },
    )
}
