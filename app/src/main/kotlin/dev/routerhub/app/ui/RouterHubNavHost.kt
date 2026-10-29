package dev.routerhub.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.routerhub.app.AppContainer
import dev.routerhub.app.ui.add.AddRouterScreen
import dev.routerhub.app.ui.browse.BrowseScreen
import dev.routerhub.app.ui.more.AboutScreen
import dev.routerhub.app.ui.more.LicensesScreen
import dev.routerhub.app.ui.more.MoreScreen
import dev.routerhub.app.ui.router.RouterDetailScreen
import dev.routerhub.app.ui.routers.RoutersScreen
import dev.routerhub.app.ui.settings.SettingsPageScreen

private enum class TopLevel(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector) {
    ROUTERS("routers", "Routers", Icons.Filled.Router, Icons.Outlined.Router),
    BROWSE("browse", "Browse", Icons.Filled.Explore, Icons.Outlined.Explore),
    MORE("more", "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
}

private object Routes {
    const val ROUTER = "router/{id}"
    const val ADD = "add?firmware={firmware}"
    const val EDIT = "edit/{id}"
    const val SETTINGS = "router/{id}/settings/{page}"
    const val ABOUT = "about"
    const val LICENSES = "about/licenses"
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
fun RouterHubNavHost(container: AppContainer) {
    val navController = rememberNavController()
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
                            icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                            label = { Text(tab.label) },
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
                BrowseScreen(container = container, onAddRouter = { navController.navigate(Routes.add(it)) })
            }
            composable(TopLevel.MORE.route) {
                MoreScreen(container = container, onOpenAbout = { navController.navigate(Routes.ABOUT) })
            }
            composable(Routes.ABOUT) {
                AboutScreen(
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
}
