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

        //  Главный экран профилей
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
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToExportImport = {
                    navController.navigate("export_import_general")
                }
            )
        }

        //  Экран списка записей профиля
        composable("vault/{profileId}") { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toIntOrNull()
            VaultListScreen(
                profileId = profileId,
                onNavigateToEntry = { entryId ->
                    navController.navigate("editor/$entryId/${profileId ?: return@VaultListScreen}")
                },
                onNavigateToNewEntry = {
                    navController.navigate("editor/new/${profileId ?: return@VaultListScreen}")
                },
                onNavigateToAudit = {
                    navController.navigate("audit/${profileId ?: return@VaultListScreen}")
                },
                onNavigateToExport = {
                    navController.navigate("export/${profileId ?: return@VaultListScreen}")
                },
                onNavigateToRotation = {
                    navController.navigate("rotation/${profileId ?: return@VaultListScreen}")
                },
                onNavigateToSettings = {
                    navController.navigate("profile_settings/${profileId ?: return@VaultListScreen}")
                },
                onNavigateToMnemonicGenerator = {
                    navController.navigate("mnemonic/${profileId ?: return@VaultListScreen}")
                },
                onNavigateToQrScanner = {
                    navController.navigate("qr_scanner/${profileId ?: return@VaultListScreen}")
                }
            )
        }

        //  Редактор записи
        composable("editor/{id}/{profileId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            val profileId = backStackEntry.arguments?.getString("profileId")?.toIntOrNull()

            EntryEditorScreen(
                id = id,
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }

        //  Мнемонический генератор
        composable("mnemonic/{profileId}") { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toIntOrNull()
            MnemonicGeneratorScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }

        //  Ротация паролей
        composable("rotation/{profileId}") { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toIntOrNull()
            RotationScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }

        //  QR-сканер
        composable("qr_scanner/{profileId}") { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toIntOrNull()
            QrScannerScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }

        //  Аудит
        composable("audit/{profileId}") { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toIntOrNull()
            AuditScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }

        //  Экспорт/импорт из профиля
        composable("export/{profileId}") { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toIntOrNull()
            ExportImportScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }

        //  Экспорт/импорт общий (из настроек)
        composable("export_import_general") {
            ExportImportScreen(
                profileId = null,
                onBack = { navController.popBackStack() }
            )
        }

        //  Настройки профиля
        composable("profile_settings/{profileId}") { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toIntOrNull()
            ProfileSettingsScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }

        //  Общие настройки
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToExport = { navController.navigate("export_import_general") },
                onNavigateToChangePassword = { navController.navigate("change_password") }
            )
        }

        composable("change_password") {
            ChangeMasterPasswordScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
