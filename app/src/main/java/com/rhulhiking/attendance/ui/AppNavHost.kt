package com.rhulhiking.attendance.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rhulhiking.attendance.ui.nfc.NfcDebugScreen
import com.rhulhiking.attendance.ui.picker.PickerScreen
import com.rhulhiking.attendance.ui.scan.ScanningScreen
import com.rhulhiking.attendance.ui.setup.SetupScreen

@Composable
fun AppNavHost(startDestination: Any, navController: NavHostController = rememberNavController()) {
    NavHost(navController, startDestination = startDestination) {
        composable<SetupRoute> {
            SetupScreen(onDone = {
                navController.navigate(PickerRoute) { popUpTo(SetupRoute) { inclusive = true } }
            })
        }
        composable<PickerRoute> {
            PickerScreen(
                onStart = { tab -> navController.navigate(ScanRoute(tab)) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onNeedsSetup = {
                    navController.navigate(SetupRoute) { popUpTo(PickerRoute) { inclusive = true } }
                },
            )
        }
        composable<ScanRoute> {
            ScanningScreen(onEndSession = { navController.popBackStack(PickerRoute, inclusive = false) })
        }
        composable<SettingsRoute> {
            SetupScreen(onDone = { navController.popBackStack(PickerRoute, inclusive = false) })
        }
        composable<NfcTestRoute> { NfcDebugScreen() }
    }
}
