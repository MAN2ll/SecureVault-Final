package com.securevault.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securevault.ui.screens.*

@Composable
fun SecureVaultNavHost() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "lock") {
        
        composable("lock") {
            LockScreen(
                onUnlocked = {
                    navController.navigate("main") { popUpTo("lock") { inclusive = true } }
                },
                onBiometricRequest = { }
            )
        }
        
        composable("main") {
            VaultListScreen(
                onAdd = { navController.navigate("generator?mode=new") },
                onEdit = { id -> navController.navigate("generator?mode=edit&id=$id") },
                onLock = {
                    navController.navigate("lock") { popUpTo("main") { inclusive = true } }
                },
                onExport = { navController.navigate("export") }
            )
        }
        
        composable(
            "generator?mode={mode}&id={id}",
            arguments = listOf(
                navArgument("mode") { defaultValue = "new" },
                navArgument("id") { nullable = true }
            )
        ) { back ->
            GeneratorScreen(
                onGenerated = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable("export") {
            ExportImportScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
