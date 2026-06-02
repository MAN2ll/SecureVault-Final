package com.securevault.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.securevault.ui.screens.*
import com.securevault.viewmodel.AuthViewModel

@Composable
fun SecureVaultNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()

    NavHost(navController, startDestination = "splash") {
        // ✅ Промежуточный экран для проверки состояния
        composable("splash") {
            when (authState) {
                is AuthViewModel.AuthState.SetupRequired -> {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        navController.navigate("setup") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Text("Загрузка...")
                    }
                }
                is AuthViewModel.AuthState.Idle,
                is AuthViewModel.AuthState.Failed,
                is AuthViewModel.AuthState.Blocked -> {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        navController.navigate("lock") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Text("Загрузка...")
                    }
                }
                is AuthViewModel.AuthState.Success -> {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        navController.navigate("main") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Text("Загрузка...")
                    }
                }
                else -> {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Text("Загрузка...")
                    }
                }
            }
        }

        composable("setup") {
            SetupScreen(
                onCompleted = {
                    navController.navigate("main") {
                        popUpTo("setup") { inclusive = true }
                    }
                }
            )
        }

        composable("lock") {
            LockScreen(
                onUnlocked = {
                    navController.navigate("main") {
                        popUpTo("lock") { inclusive = true }
                    }
                },
                onWipeTriggered = {
                    navController.navigate("setup") {
                        popUpTo("lock") { inclusive = true }
                    }
                }
            )
        }

        composable("main") {
            VaultListScreen(
                onNavigate = { route -> navController.navigate(route) },
                onLock = {
                    authViewModel.lock()
                    navController.navigate("lock") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }

        composable("editor/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            EntryEditorScreen(
                id = id,
                onBack = { navController.popBackStack() }
            )
        }

        composable("mnemonic") {
            MnemonicGeneratorScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("rotation") {
            RotationScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
