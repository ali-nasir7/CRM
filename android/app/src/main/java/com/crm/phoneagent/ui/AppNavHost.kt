package com.crm.phoneagent.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.crm.phoneagent.PhoneAgentApplication
import com.crm.phoneagent.service.PhoneAgentConnectionService
import com.crm.phoneagent.ui.screens.ConnectedScreen
import com.crm.phoneagent.ui.screens.PairDeviceScreen
import com.crm.phoneagent.ui.screens.PermissionsScreen
import com.crm.phoneagent.ui.screens.WelcomeScreen

object Routes {
    const val WELCOME = "welcome"
    const val PAIR = "pair"
    const val PERMISSIONS = "permissions"
    const val CONNECTED = "connected"
}

@Composable
fun AppNavHost() {
    val context = LocalContext.current
    val app = context.applicationContext as PhoneAgentApplication
    val navController: NavHostController = rememberNavController()

    val start = when {
        !app.deviceStore.isPaired() -> Routes.WELCOME
        !AgentPermissions.hasCallPermission(context) -> Routes.PERMISSIONS
        else -> Routes.CONNECTED
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onPairDevice = { navController.navigate(Routes.PAIR) })
        }
        composable(Routes.PAIR) {
            PairDeviceScreen(
                onBack = { navController.popBackStack() },
                onPaired = {
                    val next = if (AgentPermissions.hasCallPermission(context)) Routes.CONNECTED else Routes.PERMISSIONS
                    navController.navigate(next) { popUpTo(Routes.WELCOME) { inclusive = true } }
                },
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(
                onContinue = {
                    PhoneAgentConnectionService.start(context)
                    navController.navigate(Routes.CONNECTED) { popUpTo(Routes.WELCOME) { inclusive = true } }
                },
            )
        }
        composable(Routes.CONNECTED) {
            ConnectedScreen(
                onDisconnected = {
                    navController.navigate(Routes.WELCOME) { popUpTo(0) { inclusive = true } }
                },
            )
        }
    }
}
