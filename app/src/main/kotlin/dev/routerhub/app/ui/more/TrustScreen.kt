package dev.routerhub.app.ui.more

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.routerhub.app.BuildConfig
import dev.routerhub.app.R
import dev.routerhub.app.ui.components.SectionHeader

/** A router the official extensions were tried on, and what was tried. */
private class TestedRouter(val name: String, val firmware: String, @StringRes val tested: Int)

/** Kept in step with the extensions' docs. Only what was run against real hardware or a real firmware image. */
private val TESTED = listOf(
    TestedRouter("Xiaomi Mi Router 4C", "OpenWrt 24.10", R.string.tested_openwrt_hardware),
    TestedRouter("OpenWrt in a virtual machine", "OpenWrt 21.02, 22.03, 23.05 and 25.12", R.string.tested_openwrt_vm),
    TestedRouter("Tenda AC10 V6.0", "V16.03.61.08", R.string.tested_tenda),
    TestedRouter("TP-Link TD-W8960N V7", "", R.string.tested_tplink),
)

/** How the app treats router passwords, and which routers are known to work. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trust_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionHeader(stringResource(R.string.trust_passwords))
            Paragraphs(
                listOfNotNull(
                    R.string.trust_stored,
                    R.string.trust_sent,
                    R.string.trust_export,
                    R.string.trust_extensions,
                    R.string.trust_permissions,
                    R.string.trust_no_ads,
                    if (BuildConfig.UPDATE_CHECKER) R.string.trust_update_check else null,
                ),
            )
            SectionHeader(stringResource(R.string.tested_routers))
            Text(
                stringResource(R.string.tested_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            TESTED.forEach { router ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(router.name, style = MaterialTheme.typography.titleSmall)
                    if (router.firmware.isNotEmpty()) {
                        Text(router.firmware, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(router.tested), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Paragraphs(texts: List<Int>) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        texts.forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
    }
}
