package com.securevault.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.securevault.ui.screens.*

@Composable
fun SecureVaultNavHost() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "vault") {
        composable("vault") { VaultListScreen(onNavigate = { route -> navController.navigate(route) }) }
        composable("editor/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            EntryEditorScreen(id = id, onBack = { navController.popBackStack() })
        }
        composable("mnemonic") { MnemonicGeneratorScreen(onBack = { navController.popBackStack() }) }
        composable("rotation") { RotationScreen(onBack = { navController.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}
