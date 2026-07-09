package com.cloudflare.manager

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cloudflare.manager.ui.accounts.AccountsScreen
import com.cloudflare.manager.ui.accounts.AddAccountScreen
import com.cloudflare.manager.ui.analytics.AnalyticsScreen
import com.cloudflare.manager.ui.cache.CacheScreen
import com.cloudflare.manager.ui.components.CfBottomBar
import com.cloudflare.manager.ui.dashboard.DashboardScreen
import com.cloudflare.manager.ui.dns.DnsEditScreen
import com.cloudflare.manager.ui.dns.DnsScreen
import com.cloudflare.manager.ui.firewall.FirewallScreen
import com.cloudflare.manager.ui.help.HelpScreen
import com.cloudflare.manager.ui.kvd1.D1DetailScreen
import com.cloudflare.manager.ui.kvd1.KvDetailScreen
import com.cloudflare.manager.ui.kvd1.KvD1Screen
import com.cloudflare.manager.ui.more.MoreScreen
import com.cloudflare.manager.ui.navigation.Screen
import com.cloudflare.manager.ui.pages.PagesDetailScreen
import com.cloudflare.manager.ui.pages.PagesScreen
import com.cloudflare.manager.ui.r2.R2Screen
import com.cloudflare.manager.ui.workers.WorkerDetailScreen
import com.cloudflare.manager.ui.settings.SettingsScreen
import com.cloudflare.manager.ui.tunnels.TunnelsScreen
import com.cloudflare.manager.ui.workers.WorkersScreen
import com.cloudflare.manager.ui.zones.ZoneDetailScreen
import com.cloudflare.manager.ui.zones.ZonesScreen
import kotlinx.coroutines.launch

@Composable
fun CloudflareManagerAppContent() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainPagerScreen(
                navController = navController,
                snackbarHostState = snackbarHostState
            )
        }
        detailRoutes(navController, snackbarHostState)
    }
}

@Composable
private fun MainPagerScreen(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState
) {
    var selectedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = selectedPage) { Screen.bottomNavItems.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        selectedPage = pagerState.currentPage
    }

    Scaffold(
        bottomBar = {
            CfBottomBar(
                selectedIndex = pagerState.currentPage,
                onItemSelected = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(innerPadding),
            beyondViewportPageCount = 1
        ) { page ->
            when (Screen.bottomNavItems[page]) {
                Screen.Dashboard -> DashboardScreen(
                    snackbarHostState = snackbarHostState,
                    onNavigate = { route -> navController.navigate(route) }
                )
                Screen.Accounts -> AccountsScreen(
                    snackbarHostState = snackbarHostState,
                    onAddAccount = { navController.navigate(Screen.AddAccount.route) }
                )
                Screen.Zones -> ZonesScreen(
                    snackbarHostState = snackbarHostState,
                    onZoneClick = { zone ->
                        navController.navigate(Screen.ZoneDetail.createRoute(zone.id, zone.name))
                    }
                )
                Screen.More -> MoreScreen(
                    onNavigate = { route -> navController.navigate(route) }
                )
                else -> {}
            }
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.detailRoutes(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState
) {
    val nav = navController
    val snack = snackbarHostState

    composable(
        route = Screen.ZoneDetail.route,
        arguments = listOf(
            androidx.navigation.navArgument("zoneId") { type = androidx.navigation.NavType.StringType },
            androidx.navigation.navArgument("zoneName") { type = androidx.navigation.NavType.StringType }
        )
    ) { backStackEntry ->
        val zoneId = backStackEntry.arguments?.getString("zoneId") ?: ""
        val zoneName = backStackEntry.arguments?.getString("zoneName")?.let {
            java.net.URLDecoder.decode(it, "UTF-8")
        } ?: ""
        ZoneDetailScreen(
            zoneId = zoneId,
            zoneName = zoneName,
            snackbarHostState = snack,
            onManageDns = { nav.navigate(Screen.Dns.createRoute(zoneId, zoneName)) },
            onBack = { nav.popBackStack() }
        )
    }
    composable(
        route = Screen.Dns.route,
        arguments = listOf(
            androidx.navigation.navArgument("zoneId") { type = androidx.navigation.NavType.StringType },
            androidx.navigation.navArgument("zoneName") { type = androidx.navigation.NavType.StringType }
        )
    ) { backStackEntry ->
        val zoneId = backStackEntry.arguments?.getString("zoneId") ?: ""
        val zoneName = backStackEntry.arguments?.getString("zoneName")?.let {
            java.net.URLDecoder.decode(it, "UTF-8")
        } ?: ""
        DnsScreen(
            zoneId = zoneId,
            zoneName = zoneName,
            snackbarHostState = snack,
            onAddRecord = { nav.navigate(Screen.DnsEdit.createRoute(zoneId, null)) },
            onEditRecord = { record ->
                nav.navigate(Screen.DnsEdit.createRoute(zoneId, record.id))
            },
            onBack = { nav.popBackStack() }
        )
    }
    composable(
        route = Screen.DnsEdit.route,
        arguments = listOf(
            androidx.navigation.navArgument("zoneId") { type = androidx.navigation.NavType.StringType },
            androidx.navigation.navArgument("recordId") {
                type = androidx.navigation.NavType.StringType
                defaultValue = "new"
            }
        )
    ) { backStackEntry ->
        val zoneId = backStackEntry.arguments?.getString("zoneId") ?: ""
        val recordId = backStackEntry.arguments?.getString("recordId")
        DnsEditScreen(
            zoneId = zoneId,
            recordId = recordId.takeIf { it != "new" },
            snackbarHostState = snack,
            onBack = { nav.popBackStack() }
        )
    }
    composable(Screen.Workers.route) {
        WorkersScreen(
            onBack = { nav.popBackStack() },
            onScriptClick = { script ->
                nav.navigate(Screen.WorkerDetail.createRoute(script.name))
            }
        )
    }
    composable(
        route = Screen.WorkerDetail.route,
        arguments = listOf(
            androidx.navigation.navArgument("scriptName") { type = androidx.navigation.NavType.StringType }
        )
    ) { backStackEntry ->
        val scriptName = backStackEntry.arguments?.getString("scriptName")?.let {
            java.net.URLDecoder.decode(it, "UTF-8")
        } ?: ""
        WorkerDetailScreen(
            scriptName = scriptName,
            onBack = { nav.popBackStack() }
        )
    }
    composable(Screen.Pages.route) {
        PagesScreen(
            onBack = { nav.popBackStack() },
            onProjectClick = { project ->
                nav.navigate(Screen.PagesDetail.createRoute(project.name))
            }
        )
    }
    composable(
        route = Screen.PagesDetail.route,
        arguments = listOf(
            androidx.navigation.navArgument("projectName") { type = androidx.navigation.NavType.StringType }
        )
    ) { backStackEntry ->
        val projectName = backStackEntry.arguments?.getString("projectName")?.let {
            java.net.URLDecoder.decode(it, "UTF-8")
        } ?: ""
        PagesDetailScreen(
            projectName = projectName,
            onBack = { nav.popBackStack() }
        )
    }
    composable(Screen.R2.route) { R2Screen(onBack = { nav.popBackStack() }) }
    composable(Screen.KvD1.route) {
        KvD1Screen(
            onBack = { nav.popBackStack() },
            onNavigateToKvDetail = { id, title ->
                nav.navigate(Screen.KvDetail.createRoute(id, title))
            },
            onNavigateToD1Detail = { id, name ->
                nav.navigate(Screen.D1Detail.createRoute(id, name))
            }
        )
    }
    composable(
        route = Screen.KvDetail.route,
        arguments = listOf(
            androidx.navigation.navArgument("namespaceId") { type = androidx.navigation.NavType.StringType },
            androidx.navigation.navArgument("namespaceTitle") { type = androidx.navigation.NavType.StringType }
        )
    ) { backStackEntry ->
        val namespaceId = backStackEntry.arguments?.getString("namespaceId") ?: ""
        val namespaceTitle = backStackEntry.arguments?.getString("namespaceTitle")?.let {
            java.net.URLDecoder.decode(it, "UTF-8")
        } ?: ""
        KvDetailScreen(
            namespaceId = namespaceId,
            namespaceTitle = namespaceTitle,
            onBack = { nav.popBackStack() }
        )
    }
    composable(
        route = Screen.D1Detail.route,
        arguments = listOf(
            androidx.navigation.navArgument("databaseId") { type = androidx.navigation.NavType.StringType },
            androidx.navigation.navArgument("databaseName") { type = androidx.navigation.NavType.StringType }
        )
    ) { backStackEntry ->
        val databaseId = backStackEntry.arguments?.getString("databaseId") ?: ""
        val databaseName = backStackEntry.arguments?.getString("databaseName")?.let {
            java.net.URLDecoder.decode(it, "UTF-8")
        } ?: ""
        D1DetailScreen(
            databaseId = databaseId,
            databaseName = databaseName,
            onBack = { nav.popBackStack() }
        )
    }
    composable(Screen.Tunnels.route) { TunnelsScreen(onBack = { nav.popBackStack() }) }
    composable(Screen.Firewall.route) { FirewallScreen(onBack = { nav.popBackStack() }) }
    composable(Screen.Cache.route) { CacheScreen(onBack = { nav.popBackStack() }) }
    composable(Screen.Analytics.route) { AnalyticsScreen(onBack = { nav.popBackStack() }) }
    composable(Screen.Settings.route) { SettingsScreen(onBack = { nav.popBackStack() }) }
    composable(Screen.Help.route) { HelpScreen(onBack = { nav.popBackStack() }) }
    composable(Screen.AddAccount.route) {
        AddAccountScreen(
            snackbarHostState = snack,
            onBack = { nav.popBackStack() }
        )
    }
}
