package com.securevault.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securevault.ui.screens.GeneratorScreen
import com.securevault.ui.screens.LockScreen
import com.securevault.ui.screens.VaultListScreen

@Composable
fun SecureVaultNavHost() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "lock") {
        
        // Экран блокировки
        composable("lock") {
            LockScreen(
                onUnlocked = {
                    navController.navigate("main") {
                        popUpTo("lock") { inclusive = true }
                    }
                },
                onBiometricRequest = { /* TODO: Биометрия */ }
            )
        }
        
        //  Главный экран
        composable("main") {
            VaultListScreen(
                onAdd = { navController.navigate("generator?mode=new") },
                onEdit = { entryId -> navController.navigate("generator?mode=edit&entryId=$entryId") },
                onLock = {
                    navController.navigate("lock") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }
        
        //  Экран генератора
        composable(
            route = "generator?mode={mode}&entryId={entryId}",
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "new" },
                navArgument("entryId") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "new"
            val entryId = backStackEntry.arguments?.getString("entryId")
            
            GeneratorScreen(
                onGenerated = { password ->
                    if (mode == "new") {
                        // TODO: Создать новую запись с этим паролем
                    } else if (mode == "edit" && entryId != null) {
                        // TODO: Обновить запись entryId с новым паролем
                    }
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
