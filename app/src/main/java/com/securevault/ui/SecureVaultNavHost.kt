package com.securevault.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                onSetupRequired = {
                    navController.navigate("setup") { popUpTo("lock") { inclusive = true } }
                }
                // ✅ onBiometricRequest убран
            )
        }
        
        composable("setup") {
            SetupScreen(
                onCompleted = {
                    navController.navigate("main") { popUpTo("lock") { inclusive = true } }
                }
            )
        }
        
        composable("main") {
            VaultListScreen(
                onAdd = { navController.navigate("generator?mode=new") },
                onEdit = { id -> navController.navigate("generator?mode=edit&id=$id") },
                onLock = { navController.navigate("lock") { popUpTo("main") { inclusive = true } } },
                onExport = { navController.navigate("export") },
                onThemeChange = { /* TODO: показать диалог темы */ }
            )
        }
        
        composable("generator?mode={mode}&id={id}") {
            GeneratorScreen(
                onGenerated = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable("mnemonic_generator") {
            MnemonicGeneratorScreen(onBack = { navController.popBackStack() })
        }
        
        composable("export") {
            ExportImportScreen(onBack = { navController.popBackStack() })
        }
        
        composable("rotation") {
            RotationScreen(onBack = { navController.popBackStack() })
        }
    }
}
