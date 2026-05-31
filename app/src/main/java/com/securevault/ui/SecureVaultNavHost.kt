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
                onBiometricRequest = { },
                onSetupRequired = {
                    navController.navigate("setup") { popUpTo("lock") { inclusive = true } }
                }
            )
        }
        
        composable("setup") {
            SetupScreen(
                onCompleted = {
                    navController.navigate("main") {
                        popUpTo("lock") { inclusive = true }
                    }
                }
            )
        }
        
        composable("main") {
            VaultListScreen(
                onAdd = { navController.navigate("mnemonic_generator") },
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
        ) { backStackEntry ->
            GeneratorScreen(
                onGenerated = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable("mnemonic_generator") {
            MnemonicGeneratorScreen(
                onGenerated = { password, emoji, rotation ->
                    navController.popBackStack()
                },
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
