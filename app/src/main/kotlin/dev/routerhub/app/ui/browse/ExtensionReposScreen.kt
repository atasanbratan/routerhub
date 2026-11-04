package dev.routerhub.app.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.routerhub.app.AppContainer
import dev.routerhub.app.R
import dev.routerhub.app.extension.ExtensionRepos
import dev.routerhub.app.ui.components.RouterCover

/** Mihon's "Extension repos": where Browse finds extensions to install. The official one comes first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionReposScreen(container: AppContainer, onBack: () -> Unit) {
    val repos = container.extensionRepos
    val urls by repos.urls.collectAsStateWithLifecycle()
    val loaded by container.store.loadedRepos.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.extension_repos)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.extension_repos_add)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "warning") {
                Text(
                    stringResource(R.string.extension_repos_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (urls.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.extension_repos_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(urls, key = { it }) { url ->
                val official = url == ExtensionRepos.OFFICIAL
                val name = loaded[url]?.name
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RouterCover(size = 40.dp, icon = Icons.Outlined.Storefront)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            listOfNotNull(name, stringResource(R.string.extension_repos_official).takeIf { official })
                                .joinToString(" · ")
                                .ifEmpty { url.substringAfter("://").substringBefore('/') },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { repos.remove(url) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.extension_repo_remove, name ?: url))
                    }
                }
            }
            if (ExtensionRepos.OFFICIAL !in urls) {
                item(key = "restore") {
                    TextButton(onClick = repos::restoreOfficial, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.extension_repos_restore))
                    }
                }
            }
        }
    }

    if (adding) AddRepoDialog(onAdd = repos::add, onDismiss = { adding = false })
}

@Composable
private fun AddRepoDialog(onAdd: (String) -> ExtensionRepos.AddResult, onDismiss: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.extension_repos_add)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    error = null
                },
                label = { Text(stringResource(R.string.extension_repos_url)) },
                placeholder = { Text("https://…/index.min.json") },
                supportingText = { Text(stringResource(error ?: R.string.extension_repos_url_hint)) },
                isError = error != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        },
        confirmButton = {
            TextButton(
                enabled = input.isNotBlank(),
                onClick = {
                    when (onAdd(input)) {
                        ExtensionRepos.AddResult.ADDED -> onDismiss()
                        ExtensionRepos.AddResult.INVALID -> error = R.string.extension_repos_invalid
                        ExtensionRepos.AddResult.EXISTS -> error = R.string.extension_repos_exists
                    }
                },
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
