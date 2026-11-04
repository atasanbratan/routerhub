package dev.routerhub.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.routerhub.app.AppContainer
import dev.routerhub.app.BuildConfig
import dev.routerhub.app.R
import dev.routerhub.app.ui.add.AddRouterScreen
import dev.routerhub.app.ui.browse.BrowseScreen
import dev.routerhub.app.ui.browse.ExtensionDetailsScreen
import dev.routerhub.app.ui.browse.ExtensionReposScreen
import dev.routerhub.app.ui.more.AboutScreen
import dev.routerhub.app.ui.more.LicensesScreen
import dev.routerhub.app.ui.more.MoreScreen
import dev.routerhub.app.ui.more.NewUpdateDialog
import dev.routerhub.app.ui.more.TrustScreen
import dev.routerhub.app.ui.router.RouterDetailScreen
import dev.routerhub.app.ui.routers.RoutersScreen
import dev.routerhub.app.ui.settings.SettingsPageScreen
import dev.routerhub.app.update.AppRelease
import dev.routerhub.app.update.AppUpdateChecker
import dev.routerhub.app.update.UpdateChecks
import dev.routerhub.app.update.UpdateResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

private enum class TopLevel(val route: String, @StringRes val label: Int, val selected: ImageVector, val unselected: ImageVector) {
    ROUTERS("routers", R.string.routers, Icons.Filled.Router, Icons.Outlined.Router),
    BROWSE("browse", R.string.browse, Icons.Filled.Explore, Icons.Outlined.Explore),
    MORE("more", R.string.more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
}

private object Routes {
    const val ROUTER = "router/{id}"
    const val ADD = "add?firmware={firmware}"
    const val EDIT = "edit/{id}"
    const val SETTINGS = "router/{id}/settings/{page}"
    const val ABOUT = "about"
    const val LICENSES = "about/licenses"
    const val TRUST = "more/trust"
    const val REPOS = "browse/repos"
    const val EXTENSION = "browse/extension/{package}"
    fun extension(packageName: String) = "browse/extension/$packageName"
    fun router(id: String) = "router/$id"
    fun settings(id: String, page: String) = "router/$id/settings/$page"
    fun edit(id: String) = "edit/$id"
    fun add(firmwareId: String? = null) = if (firmwareId == null) "add" else "add?firmware=$firmwareId"
}

/**
 * App skeleton in the Tachiyomi/Mihon layout: a bottom navigation bar for the top-level tabs, and
 * full-screen detail pages pushed over it.
 */
@Composable
fun RouterHubNavHost(container: AppContainer, openRouter: String? = null, open: String? = null) {
    val navController = rememberNavController()
    val uriHandler = LocalUriHandler.current
    var newRelease by remember { mutableStateOf<AppRelease?>(null) }
    var browseTab by remember { mutableStateOf<Int?>(null) }
    val updateCount by container.extensionUpdateCount.collectAsStateWithLifecycle()
    LaunchedEffect(openRouter) {
        if (openRouter != null && container.routers.get(openRouter) != null) navController.navigate(Routes.router(openRouter))
    }
    LaunchedEffect(open) {
        if (open == UpdateChecks.OPEN_EXTENSIONS) {
            browseTab = 1
            navController.navigate(TopLevel.BROWSE.route)
        }
        newRelease = startupUpdateCheck(container, askedFor = open == UpdateChecks.OPEN_APP_UPDATE)
    }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TopLevel.entries.any { it.route == currentRoute }

    Scaffold(
        // Each screen draws its own top app bar and status-bar inset.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevel.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                val icon = if (selected) tab.selected else tab.unselected
                                if (tab == TopLevel.BROWSE && updateCount > 0) {
                                    BadgedBox(badge = { Badge { Text("$updateCount") } }) { Icon(icon, contentDescription = null) }
                                } else {
                                    Icon(icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevel.ROUTERS.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevel.ROUTERS.route) {
                RoutersScreen(
                    container = container,
                    onOpenRouter = { navController.navigate(Routes.router(it)) },
                    onAddRouter = { navController.navigate(Routes.add()) },
                    onOpenBrowse = { navController.navigate(TopLevel.BROWSE.route) },
                )
            }
            composable(TopLevel.BROWSE.route) {
                BrowseScreen(
                    container = container,
                    requestedTab = browseTab,
                    onTabShown = { browseTab = null },
                    onAddRouter = { navController.navigate(Routes.add(it)) },
                    onOpenRepos = { navController.navigate(Routes.REPOS) },
                    onOpenExtension = { navController.navigate(Routes.extension(it)) },
                )
            }
            composable(TopLevel.MORE.route) {
                MoreScreen(
                    container = container,
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenTrust = { navController.navigate(Routes.TRUST) },
                    onOpenRepos = { navController.navigate(Routes.REPOS) },
                )
            }
            composable(Routes.EXTENSION, arguments = listOf(navArgument("package") { type = NavType.StringType })) { entry ->
                ExtensionDetailsScreen(
                    container = container,
                    packageName = entry.arguments?.getString("package").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.REPOS) {
                ExtensionReposScreen(container = container, onBack = { navController.popBackStack() })
            }
            composable(Routes.TRUST) {
                TrustScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenLicenses = { navController.navigate(Routes.LICENSES) },
                )
            }
            composable(Routes.LICENSES) {
                LicensesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ROUTER, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                RouterDetailScreen(
                    container = container,
                    profileId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.edit(id)) },
                    onOpenSettings = { page -> navController.navigate(Routes.settings(id, page)) },
                )
            }
            composable(
                Routes.SETTINGS,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("page") { type = NavType.StringType },
                ),
            ) { entry ->
                SettingsPageScreen(
                    container = container,
                    profileId = entry.arguments?.getString("id").orEmpty(),
                    pageId = entry.arguments?.getString("page").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                AddRouterScreen(
                    container = container,
                    initialFirmwareId = null,
                    editProfileId = entry.arguments?.getString("id"),
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                Routes.ADD,
                arguments = listOf(navArgument("firmware") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                AddRouterScreen(
                    container = container,
                    initialFirmwareId = entry.arguments?.getString("firmware"),
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(Routes.router(id)) {
                            popUpTo(TopLevel.ROUTERS.route)
                        }
                    },
                )
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

/**
 * Mihon's check on start: at most once a day while automatic updates are on, or right away when an
 * update notification was tapped. Also refreshes the extension list, for the badge on Browse.
 */
private suspend fun startupUpdateCheck(container: AppContainer, askedFor: Boolean): AppRelease? {
    val prefs = container.preferences
    if (BuildConfig.INSTALLABLE_EXTENSIONS && prefs.autoUpdates.value) container.store.refreshIfStale()
    if (!BuildConfig.UPDATE_CHECKER) return null
    val now = System.currentTimeMillis()
    if (!askedFor && (!prefs.autoUpdates.value || now - prefs.lastAppUpdateCheck < TimeUnit.DAYS.toMillis(1))) return null
    prefs.lastAppUpdateCheck = now
    val result = try {
        AppUpdateChecker().check()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return null
    }
    return (result as? UpdateResult.NewUpdate)?.release
}
