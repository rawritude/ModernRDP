package com.modernrdp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.modernrdp.ui.screens.editor.ConnectionEditorScreen
import com.modernrdp.ui.screens.home.HomeScreen
import com.modernrdp.ui.screens.session.SessionScreen
import com.modernrdp.ui.screens.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor?connectionId={connectionId}"
    const val SESSION = "session/{connectionId}"
    const val SETTINGS = "settings"

    fun editor(connectionId: Long? = null): String =
        if (connectionId != null) "editor?connectionId=$connectionId" else "editor"

    fun session(connectionId: Long): String = "session/$connectionId"
}

@Composable
fun ModernRdpNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddConnection = { navController.navigate(Routes.editor()) },
                onEditConnection = { id -> navController.navigate(Routes.editor(id)) },
                onConnect = { id -> navController.navigate(Routes.session(id)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onQuickConnect = { id -> navController.navigate(Routes.session(id)) },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("connectionId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { backStackEntry ->
            val connectionId = backStackEntry.arguments?.getLong("connectionId") ?: -1L
            ConnectionEditorScreen(
                connectionId = if (connectionId == -1L) null else connectionId,
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.SESSION,
            arguments = listOf(
                navArgument("connectionId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val connectionId = backStackEntry.arguments?.getLong("connectionId") ?: return@composable
            SessionScreen(
                connectionId = connectionId,
                onDisconnected = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
