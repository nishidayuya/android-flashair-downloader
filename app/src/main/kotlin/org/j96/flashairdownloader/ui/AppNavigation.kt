package org.j96.flashairdownloader.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.j96.flashairdownloader.ui.browse.BrowseRoute
import org.j96.flashairdownloader.ui.home.HomeRoute

private const val ROUTE_HOME = "home"
private const val ROUTE_BROWSE = "browse"

@Composable
fun FlashAirDownloaderApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeRoute(onBrowseClick = { navController.navigate(ROUTE_BROWSE) })
        }
        composable(ROUTE_BROWSE) {
            BrowseRoute(onNavigateBack = { navController.popBackStack() })
        }
    }
}
