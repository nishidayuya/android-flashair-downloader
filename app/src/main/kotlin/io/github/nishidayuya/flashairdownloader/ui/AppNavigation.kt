package io.github.nishidayuya.flashairdownloader.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.nishidayuya.flashairdownloader.ui.browse.BrowseRoute
import io.github.nishidayuya.flashairdownloader.ui.history.HistoryRoute
import io.github.nishidayuya.flashairdownloader.ui.home.HomeRoute
import io.github.nishidayuya.flashairdownloader.ui.settings.SettingsRoute
import io.github.nishidayuya.flashairdownloader.ui.sync.SyncRoute

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
