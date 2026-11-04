package dev.routerhub.app.ui.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.routerhub.api.ExtensionContract
import dev.routerhub.app.AppContainer
import dev.routerhub.app.BuildConfig
import dev.routerhub.app.R
import dev.routerhub.app.ui.components.RouterCover
import dev.routerhub.app.update.AppRelease
import dev.routerhub.app.update.AppUpdateChecker
import dev.routerhub.app.update.UpdateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val GITHUB_URL = "https://github.com/${BuildConfig.GITHUB_REPO}"
private const val RELEASE_URL = "$GITHUB_URL/releases/tag/v${BuildConfig.VERSION_NAME}"
private const val PRIVACY_URL = "${BuildConfig.WEBSITE_URL}/privacy"

/** Mihon's About: a logo, version, update check, what's new, licenses, privacy policy, then link icons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(container: AppContainer, onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val checker = remember { AppUpdateChecker() }
    var checking by remember { mutableStateOf(false) }
    var newRelease by remember { mutableStateOf<AppRelease?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RouterCover(size = 72.dp)
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            }
            HorizontalDivider()

            AboutItem(
                title = stringResource(R.string.version),
                subtitle = versionLabel(),
                onClick = {
                    copyToClipboard(context, context.getString(R.string.debug_info), debugInfo())
                    Toast.makeText(context, R.string.debug_info_copied, Toast.LENGTH_SHORT).show()
                },
            )
            // Play delivers its own updates, like Mihon's non-GitHub builds.
            if (BuildConfig.UPDATE_CHECKER) AboutItem(
                title = stringResource(R.string.check_for_updates),
                trailing = {
                    AnimatedVisibility(checking) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp) }
                },
                onClick = {
                    if (!checking) {
                        checking = true
                        scope.launch {
                            try {
                                when (val result = checker.check()) {
                                    is UpdateResult.NewUpdate -> newRelease = result.release
                                    UpdateResult.NoNewUpdate ->
                                        Toast.makeText(context, R.string.no_new_updates, Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.update_check_failed, e.message), Toast.LENGTH_LONG).show()
                            } finally {
                                checking = false
                            }
                        }
                    }
                },
            )
            if (!BuildConfig.DEBUG) {
                AboutItem(title = stringResource(R.string.whats_new), onClick = { uriHandler.openUri(RELEASE_URL) })
            }
            AboutItem(title = stringResource(R.string.licenses), onClick = onOpenLicenses)
            AboutItem(title = stringResource(R.string.privacy_policy), onClick = { uriHandler.openUri(PRIVACY_URL) })

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { uriHandler.openUri(BuildConfig.WEBSITE_URL) }) {
                    Icon(Icons.Outlined.Public, contentDescription = stringResource(R.string.website), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { uriHandler.openUri(GITHUB_URL) }) {
                    Icon(
                        painterResource(R.drawable.ic_github),
                        contentDescription = stringResource(R.string.github),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    newRelease?.let { release ->
        NewUpdateDialog(
            release = release,
            onDownload = {
                container.appUpdates.download(release)
                newRelease = null
            },
            onOpenRelease = { uriHandler.openUri(release.releaseUrl) },
            onDismiss = { newRelease = null },
        )
    }
}

@Composable
private fun AboutItem(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
internal fun NewUpdateDialog(release: AppRelease, onDownload: () -> Unit, onOpenRelease: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_version)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("v${release.version}", style = MaterialTheme.typography.titleMedium)
                Text(
                    releaseNotes(release.changelog),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(onClick = onOpenRelease, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.open_on_github))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDownload) { Text(stringResource(R.string.download)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
    )
}

/**
 * Just enough Markdown for the notes the release workflow writes: headings and bullets. The checksums
 * after the first `---` are for the GitHub page and are left out here.
 */
private fun releaseNotes(markdown: String): AnnotatedString = buildAnnotatedString {
    val lines = markdown.replace("\r", "").substringBefore("\n---").lines()
        .map { it.trimEnd() }
        .dropWhile { it.isBlank() }
    lines.forEachIndexed { index, raw ->
        val line = raw.replace("**", "").replace("`", "")
        val bullet = line.trimStart()
        when {
            line.startsWith("#") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(line.trimStart('#').trim())
            }
            bullet.startsWith("- ") || bullet.startsWith("* ") -> append("• ${bullet.drop(2)}")
            else -> append(line)
        }
        if (index != lines.lastIndex) append('\n')
    }
}

@Composable
private fun versionLabel(): String = stringResource(
    if (BuildConfig.DEBUG) R.string.version_debug else R.string.version_stable,
    BuildConfig.VERSION_NAME,
)

private fun debugInfo(): String = buildString {
    appendLine(
        "App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR} ${BuildConfig.BUILD_TYPE}, ${BuildConfig.VERSION_CODE})",
    )
    appendLine("Extension API: v${ExtensionContract.MIN_SUPPORTED_API_VERSION}–v${ExtensionContract.API_VERSION}")
    appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
    append("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}
