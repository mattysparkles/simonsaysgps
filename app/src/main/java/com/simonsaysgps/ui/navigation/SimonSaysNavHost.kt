package com.simonsaysgps.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.simonsaysgps.ui.screen.ActiveNavigationScreen
import com.simonsaysgps.ui.screen.MapSearchScreen
import com.simonsaysgps.ui.screen.RoutePreviewScreen
import com.simonsaysgps.ui.screen.SettingsScreen
import com.simonsaysgps.ui.screen.SplashScreen
import com.simonsaysgps.ui.screen.explore.ExploreResultsScreen
import com.simonsaysgps.ui.screen.explore.ExploreScreen
import com.simonsaysgps.ui.screen.explore.ExploreSettingsScreen
import com.simonsaysgps.ui.viewmodel.AppViewModel

@Composable
fun SimonSaysNavHost(
    appViewModel: AppViewModel = hiltViewModel(),
    requestLocationPermission: () -> Unit
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen(onFinished = { navController.navigate(Screen.Map.route) { popUpTo(Screen.Splash.route) { inclusive = true } } })
        }
        composable(Screen.Map.route) {
            MapSearchScreen(
                viewModel = appViewModel,
                onRouteReady = { navController.navigate(Screen.RoutePreview.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onExploreClick = { navController.navigate(Screen.Explore.route) },
                requestLocationPermission = requestLocationPermission
            )
        }
        composable(Screen.Explore.route) {
            ExploreScreen(
                viewModel = appViewModel,
                onMapClick = { navController.navigate(Screen.Map.route) },
                onExploreResults = { navController.navigate(Screen.ExploreResults.route) },
                onExploreSettings = { navController.navigate(Screen.ExploreSettings.route) }
            )
        }
        composable(Screen.ExploreResults.route) {
            ExploreResultsScreen(
                viewModel = appViewModel,
                onBack = { navController.popBackStack() },
                onPreviewOnMap = { navController.navigate(Screen.Map.route) },
                onStartNavigation = { navController.navigate(Screen.RoutePreview.route) }
            )
        }
        composable(Screen.ExploreSettings.route) {
            ExploreSettingsScreen(
                viewModel = appViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.RoutePreview.route) {
            RoutePreviewScreen(
                viewModel = appViewModel,
                onStartNavigation = { navController.navigate(Screen.Navigation.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Navigation.route) {
            ActiveNavigationScreen(
                viewModel = appViewModel,
                onBack = {
                    appViewModel.endNavigation("navigation cancelled from UI")
                    navController.popBackStack(Screen.Map.route, false)
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = appViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
