package dev.routerhub.app.ui.browse

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.routerhub.api.RouterFirmwareFactory
import dev.routerhub.app.AppContainer
import dev.routerhub.app.BuildConfig
import dev.routerhub.app.R
import dev.routerhub.app.basic.BasicFactory
import dev.routerhub.app.extension.AvailableExtension
import dev.routerhub.app.extension.ExtensionState
import dev.routerhub.app.extension.InstallFailure
import dev.routerhub.app.extension.InstallStep
import dev.routerhub.app.extension.TrustLevel
import dev.routerhub.app.ui.components.EmptyScreen
import dev.routerhub.app.ui.components.ProtocolBadge
import dev.routerhub.app.ui.components.RouterCover
import dev.routerhub.app.ui.firmwareTitle

/**
 * Mihon's Browse, translated: "Firmwares" plays the role of Sources, "Extensions" stays. Both tabs
 * search from the top bar. Builds that can install extensions also list what the extension
 * repositories offer; the Play build has its extensions built in, so it only lists those.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(container: AppContainer, onAddRouter: (firmwareId: String) -> Unit, onOpenRepos: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val extensions by container.extensions.extensions.collectAsStateWithLifecycle()
    val store = container.store
    val repos by container.extensionRepos.urls.collectAsStateWithLifecycle()
    if (STORE) {
        LaunchedEffect(repos) { store.refreshIfStale() }
        StoreFailures(container)
    }
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (searching) SearchField(query, onChange = { query = it }) else Text(stringResource(R.string.browse))
                    },
                    navigationIcon = {
                        if (searching) {
                            IconButton(onClick = { searching = false; query = "" }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    },
                    actions = {
                        if (!searching) {
                            IconButton(onClick = { searching = true }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                            }
                        } else if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                        if (STORE && tab == 1) {
                            IconButton(onClick = store::refresh) {
                                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
                            }
                            IconButton(onClick = onOpenRepos) {
                                Icon(Icons.Outlined.Storefront, contentDescription = stringResource(R.string.extension_repos))
                            }
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.firmwares)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.extensions)) })
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        val search = query.trim()
        when (tab) {
            0 -> FirmwaresTab(extensions, search, onAddRouter, modifier)
            else -> ExtensionsTab(container, extensions, search, modifier)
        }
    }
}

private val STORE = BuildConfig.INSTALLABLE_EXTENSIONS

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.search)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** A failed install says why once, then its row shows its button again. */
@Composable
private fun StoreFailures(container: AppContainer) {
    val context = LocalContext.current
    val installs by container.store.installs.collectAsStateWithLifecycle()
    LaunchedEffect(installs) {
        installs.forEach { (packageName, step) ->
            if (step is InstallStep.Failed) {
                val reason = context.getString(
                    when (step.reason) {
                        InstallFailure.DOWNLOAD -> R.string.store_error_download
                        InstallFailure.CHECKSUM -> R.string.store_error_checksum
                        InstallFailure.SIGNER -> R.string.store_error_signer
                        InstallFailure.NO_INSTALLER -> R.string.store_error_installer
                    },
                )
                Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                container.store.clear(packageName)
            }
        }
    }
}

private fun String.matches(query: String) = contains(query, ignoreCase = true)

private fun label(state: ExtensionState) = state.info.label.removePrefix("RouterHub: ")

/** [label], with basic mode's translated: it is the app's own, not an extension's. */
@Composable
private fun shownLabel(state: ExtensionState) =
    if (state.info.packageName == BasicFactory.ID) stringResource(R.string.basic_title) else label(state)

@Composable
private fun FirmwaresTab(extensions: List<ExtensionState>, query: String, onAddRouter: (String) -> Unit, modifier: Modifier) {
    val loaded = extensions.filterIsInstance<ExtensionState.Loaded>()
    if (loaded.isEmpty()) {
        EmptyScreen(stringResource(R.string.no_firmwares), modifier)
        return
    }
    val basicNames = listOf(stringResource(R.string.basic_name), stringResource(R.string.basic_vendor))
    fun RouterFirmwareFactory.matches(extension: ExtensionState) = query.isEmpty() ||
        label(extension).matches(query) || displayName.matches(query) || vendor.matches(query) ||
        (id == BasicFactory.ID && basicNames.any { it.matches(query) })
    val shown = loaded.map { it to it.factories.filter { factory -> factory.matches(it) } }.filter { it.second.isNotEmpty() }
    if (shown.isEmpty()) {
        EmptyScreen(stringResource(R.string.no_search_results, query), modifier)
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        shown.forEach { (extension, factories) ->
            item(key = "header:${extension.info.packageName}") { SectionTitle(shownLabel(extension)) }
            items(factories, key = { it.id }) { factory ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onAddRouter(factory.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RouterCover(size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            firmwareTitle(factory),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            pluralStringResource(R.plurals.firmware_features, factory.capabilities.size, factory.capabilities.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ProtocolBadge(factory.protocol)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ExtensionsTab(container: AppContainer, extensions: List<ExtensionState>, query: String, modifier: Modifier) {
    val store = container.store
    val available by store.available.collectAsStateWithLifecycle()
    val loadedRepos by store.loadedRepos.collectAsStateWithLifecycle()
    val failedRepos by store.failedRepos.collectAsStateWithLifecycle()
    val loading by store.loading.collectAsStateWithLifecycle()
    val installs by store.installs.collectAsStateWithLifecycle()

    // The newest compatible version of each package; an incompatible one only if that's all there is.
    val offers = if (STORE) {
        available.groupBy { it.packageName }.mapValues { (_, offers) ->
            offers.filter { it.compatible }.maxByOrNull { it.versionCode } ?: offers.maxBy { it.versionCode }
        }
    } else {
        emptyMap()
    }
    val installedNames = extensions.map { it.info.packageName }.toSet()
    fun ExtensionState.shown() = query.isEmpty() || label(this).matches(query) || info.packageName.matches(query)
    fun AvailableExtension.shown() = query.isEmpty() || name.matches(query) || packageName.matches(query)

    val installed = extensions.filter { it.shown() }
    val updates = installed.mapNotNull { state ->
        val offer = offers[state.info.packageName] ?: return@mapNotNull null
        if (offer.compatible && offer.versionCode > state.info.versionCode) state to offer else null
    }
    val notInstalled = offers.values.filter { it.packageName !in installedNames && it.shown() }.sortedBy { it.name.lowercase() }
    val showRepoName = loadedRepos.size > 1

    if (installed.isEmpty() && notInstalled.isEmpty() && !loading && failedRepos.isEmpty()) {
        EmptyScreen(stringResource(if (query.isEmpty()) R.string.no_extensions else R.string.no_search_results, query), modifier)
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        if (updates.isNotEmpty()) {
            item(key = "updates") { SectionTitle(stringResource(R.string.extensions_updates)) }
            items(updates, key = { "update:${it.first.info.packageName}" }) { (state, offer) ->
                StoreRow(
                    title = label(state),
                    subtitle = "v${state.info.versionName} → v${offer.versionName}",
                    step = installs[offer.packageName],
                    action = stringResource(R.string.extension_update),
                    onAction = { store.install(offer) },
                )
            }
        }
        if (installed.isNotEmpty()) {
            if (STORE) item(key = "installed") { SectionTitle(stringResource(R.string.extensions_installed)) }
            items(installed, key = { it.info.packageName }) { state ->
                InstalledRow(state, onTrust = container.extensions::trust)
            }
        }
        if (STORE) storeSection(notInstalled, failedRepos, loadedRepos.keys, loading, installs, showRepoName, onInstall = store::install)
    }
}

private fun LazyListScope.storeSection(
    offers: List<AvailableExtension>,
    failedRepos: List<String>,
    loadedRepos: Set<String>,
    loading: Boolean,
    installs: Map<String, InstallStep>,
    showRepoName: Boolean,
    onInstall: (AvailableExtension) -> Unit,
) {
    if (offers.isEmpty() && !loading && failedRepos.isEmpty()) return
    item(key = "available") { SectionTitle(stringResource(R.string.extensions_available)) }
    if (loading && offers.isEmpty()) {
        item(key = "loading") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.store_loading), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    items(failedRepos.filter { it !in loadedRepos }, key = { "failed:$it" }) { url ->
        Text(
            stringResource(R.string.store_repo_failed, url),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    items(offers, key = { "offer:${it.packageName}" }) { offer ->
        val context = LocalContext.current
        val details = listOfNotNull(
            "v${offer.versionName}",
            offer.size.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
            offer.repo.name.takeIf { showRepoName },
        ).joinToString(" · ")
        StoreRow(
            title = offer.name,
            subtitle = if (offer.compatible) details else stringResource(R.string.extension_needs_newer_app),
            error = !offer.compatible,
            step = installs[offer.packageName],
            action = stringResource(R.string.extension_install).takeIf { offer.compatible },
            onAction = { onInstall(offer) },
        )
    }
}

@Composable
private fun StoreRow(
    title: String,
    subtitle: String,
    step: InstallStep?,
    action: String?,
    onAction: () -> Unit,
    error: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RouterCover(size = 40.dp, icon = Icons.Outlined.Extension)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            step is InstallStep.Downloading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            action != null -> TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun InstalledRow(state: ExtensionState, onTrust: (ExtensionState.Untrusted) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RouterCover(size = 40.dp, icon = Icons.Outlined.Extension)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(shownLabel(state), style = MaterialTheme.typography.bodyLarge)
            Text(
                "v${state.info.versionName} · ${statusLine(state)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (state is ExtensionState.Loaded) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state is ExtensionState.Untrusted) {
                Text(
                    stringResource(R.string.extension_signer, state.info.signerSha256.firstOrNull()?.take(16) ?: stringResource(R.string.extension_unsigned)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state is ExtensionState.Untrusted) {
            TextButton(onClick = { onTrust(state) }) { Text(stringResource(R.string.trust)) }
        } else if (state is ExtensionState.Loaded) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                state.factories.map { it.protocol }.distinct().forEach { ProtocolBadge(it) }
            }
        }
    }
}

@Composable
private fun statusLine(state: ExtensionState): String = when (state) {
    is ExtensionState.Loaded -> when (state.trust) {
        TrustLevel.OFFICIAL -> stringResource(R.string.trust_official)
        TrustLevel.DEVELOPMENT -> stringResource(R.string.trust_development)
        TrustLevel.USER_TRUSTED -> stringResource(R.string.trust_user)
        TrustLevel.BUILT_IN -> stringResource(R.string.trust_built_in)
    }
    is ExtensionState.Untrusted -> stringResource(R.string.trust_unknown)
    is ExtensionState.Incompatible -> state.reason
    is ExtensionState.Failed -> stringResource(R.string.extension_failed, state.error.message ?: state.error::class.java.simpleName)
}
