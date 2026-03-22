package com.simonsaysgps.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.simonsaysgps.ui.screen.ActiveNavigationScreen
import com.simonsaysgps.ui.screen.MapSearchScreen
import com.simonsaysgps.ui.screen.RoutePreviewScreen
import com.simonsaysgps.ui.screen.SettingsScreen
import com.simonsaysgps.ui.screen.SplashScreen
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
                requestLocationPermission = requestLocationPermission
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
                    appViewModel.endNavigation()
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
