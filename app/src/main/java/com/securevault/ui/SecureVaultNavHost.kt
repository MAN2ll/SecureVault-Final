package com.securevault.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.securevault.ui.screens.LockScreen
import com.securevault.ui.screens.VaultListScreen

@Composable
fun SecureVaultNavHost() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "lock") {
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
        composable("main") {
            VaultListScreen(
                onAdd = { /* TODO: Добавить запись */ },
                onEdit = { /* TODO: Редактировать */ },
                onLock = {
                    navController.navigate("lock") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }
    }
}
