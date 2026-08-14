package org.j96.flashairdownloader.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.j96.flashairdownloader.ui.browse.BrowseRoute
import org.j96.flashairdownloader.ui.history.HistoryRoute
import org.j96.flashairdownloader.ui.home.HomeRoute
import org.j96.flashairdownloader.ui.settings.SettingsRoute
import org.j96.flashairdownloader.ui.sync.SyncRoute

private const val ROUTE_HOME = "home"
private const val ROUTE_BROWSE = "browse"
private const val ROUTE_SYNC = "sync"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_HISTORY = "history"

@Composable
fun FlashAirDownloaderApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeRoute(
                onBrowseClick = { navController.navigate(ROUTE_BROWSE) },
                onSyncStarted = { navController.navigate(ROUTE_SYNC) },
                onSettingsClick = { navController.navigate(ROUTE_SETTINGS) },
                onHistoryClick = { navController.navigate(ROUTE_HISTORY) },
            )
        }
        composable(ROUTE_BROWSE) {
            BrowseRoute(onNavigateBack = { navController.popBackStack() })
        }
        composable(ROUTE_SYNC) {
            SyncRoute(onDone = { navController.popBackStack() })
        }
        composable(ROUTE_SETTINGS) {
            SettingsRoute(onNavigateBack = { navController.popBackStack() })
        }
        composable(ROUTE_HISTORY) {
            HistoryRoute(onNavigateBack = { navController.popBackStack() })
        }
    }
}
