package com.securevault.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.securevault.ui.screens.*
import com.securevault.viewmodel.AuthViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SecureVaultNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()

    NavHost(
        navController = navController, 
        startDestination = "splash",
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(animationSpec = tween(300)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn(animationSpec = tween(300)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(animationSpec = tween(300)) }
    ) {
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
                navController.navigate("profiles") { popUpTo("setup") { inclusive = true } }
            })
        }

        composable("lock") {
            LockScreen(
                onUnlocked = { 
                    navController.navigate("profiles") { popUpTo("lock") { inclusive = true } } 
                },
                onSetupRequired = { 
                    navController.navigate("setup") { popUpTo("lock") { inclusive = true } } 
                }
            )
        }

        composable("profiles") {
            ProfileListScreen(
                onProfileSelected = { profileId ->
                    navController.navigate("vault/$profileId") {
                        popUpTo("profiles") { saveState = true }
                        launchSingleTop = true
                    }
                },
                onLock = {
                    authViewModel.lock()
                    navController.navigate("lock") { popUpTo("profiles") { inclusive = true } }
                }
            )
        }

        composable("vault/{profileId}") { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toIntOrNull()
            VaultListScreen(
                profileId = profileId,
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() },
                onLock = {
                    authViewModel.lock()
                    navController.navigate("lock") { popUpTo("profiles") { inclusive = true } }
                }
            )
        }

        composable("editor/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            // ✅ ПРАВИЛЬНОЕ получение profileId
            val vaultEntry = navController.getBackStackEntry("vault/{profileId}")
            val profileId = vaultEntry.arguments?.getString("profileId")?.toIntOrNull()
            
            EntryEditorScreen(
                id = id, 
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }

        composable("mnemonic") {
            MnemonicGeneratorScreen(onBack = { navController.popBackStack() })
        }

        composable("rotation") {
            RotationScreen(onBack = { navController.popBackStack() })
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable("audit") {
            AuditScreen(onBack = { navController.popBackStack() })
        }

        composable("export") {
            ExportImportScreen(onBack = { navController.popBackStack() })
        }
    }
}
