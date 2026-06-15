package com.securevault.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    NavHost(navController = navController, startDestination = "splash") {
        // ✅ НОВЫЙ SPLASH SCREEN
        composable("splash") {
            SplashScreen(
                onTimeout = {
                    when (authState) {
                        is AuthViewModel.AuthState.SetupRequired -> {
                            navController.navigate("setup") { popUpTo("splash") { inclusive = true } }
                        }
                        else -> {
                            navController.navigate("lock") { popUpTo("splash") { inclusive = true } }
                        }
                    }
                }
            )
        }

        composable("setup") {
            SetupScreen(onCompleted = {
                navController.navigate("main") { popUpTo("setup") { inclusive = true } }
            })
        }

        composable("lock") {
            LockScreen(
                onUnlocked = { navController.navigate("main") { popUpTo("lock") { inclusive = true } } },
                onSetupRequired = { navController.navigate("setup") { popUpTo("lock") { inclusive = true } } }
            )
        }

        composable("main") {
            VaultListScreen(
                onNavigate = { route -> navController.navigate(route) },
                onLock = {
                    authViewModel.lock()
                    navController.navigate("lock") { popUpTo("main") { inclusive = true } }
                }
            )
        }

        composable("editor/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            EntryEditorScreen(id = id, onBack = { navController.popBackStack() })
        }

        composable("mnemonic") {
            MnemonicGeneratorScreen(onBack = { navController.popBackStack() })
        }

        composable("rotation") {
            RotationScreen(onBack = { navController.popBackStack() })
        }

        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
