package com.securevault.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.securevault.ui.screens.*
import com.securevault.utils.AutoLockManager
import com.securevault.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SecureVaultNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()
    
    // ✅ АВТОБЛОКИРОВКА
    val isLocked by AutoLockManager.isLocked.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    // Отслеживаем активность пользователя
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                AutoLockManager.recordUserActivity()
                delay(1000) // Проверяем каждую секунду
            }
        }
    }
    
    // Проверяем таймаут
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000) // Проверяем каждые 5 секунд
            if (AutoLockManager.checkTimeout()) {
                navController.navigate("lock") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
            }
        }
    }
    
    // Блокируем при сворачивании
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
            AutoLockManager.lock()
        }
    }

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
                    AutoLockManager.unlock()
                    navController.navigate("profiles") { popUpTo("lock") { inclusive = true } } 
                },
                onSetupRequired = { navController.navigate("setup") { popUpTo("lock") { inclusive = true } } }
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
                    AutoLockManager.lock()
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
                    AutoLockManager.lock()
                    navController.navigate("lock") { popUpTo("profiles") { inclusive = true } }
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
