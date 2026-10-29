package dev.routerhub.app.ui.routers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.routerhub.api.RouterFirmwareFactory
import dev.routerhub.app.AppContainer
import dev.routerhub.app.data.RouterProfile
import dev.routerhub.app.ui.components.EmptyScreen
import dev.routerhub.app.ui.components.ProtocolBadge
import dev.routerhub.app.ui.components.RouterCover

/** The "library": every saved router, Mihon-style list with cover tiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutersScreen(
    container: AppContainer,
    onOpenRouter: (String) -> Unit,
    onAddRouter: () -> Unit,
    onOpenBrowse: () -> Unit,
) {
    val routers by container.routers.profiles.collectAsStateWithLifecycle()
    val firmwares by container.extensions.firmwares.collectAsStateWithLifecycle(initialValue = emptyMap())
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Routers") }, scrollBehavior = scrollBehavior) },
        floatingActionButton = {
            if (firmwares.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("Add router") },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    onClick = onAddRouter,
                    expanded = scrollBehavior.state.overlappedFraction == 0f,
                )
            }
        },
    ) { padding ->
        when {
            routers.isEmpty() && firmwares.isEmpty() -> EmptyScreen(
                "No router extensions installed.\nInstall one, then find it under Browse.",
                modifier = Modifier.padding(padding),
                action = { FilledTonalButton(onClick = onOpenBrowse) { Text("Open Browse") } },
            )
            routers.isEmpty() -> EmptyScreen(
                "Your router list is empty.\nAdd your first router to get started.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 88.dp),
            ) {
                items(routers, key = { it.id }) { profile ->
                    RouterListItem(profile, firmwares[profile.firmwareId], onClick = { onOpenRouter(profile.id) })
                }
            }
        }
    }
}

@Composable
private fun RouterListItem(profile: RouterProfile, factory: RouterFirmwareFactory?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RouterCover(size = 56.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                factory?.let { "${it.vendor} ${it.displayName}" } ?: "Extension not installed",
                style = MaterialTheme.typography.bodySmall,
                color = if (factory == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtocolBadge(profile.protocol)
                Text(
                    "${profile.host}:${profile.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
