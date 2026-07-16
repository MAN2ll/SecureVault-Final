@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securevault.ui.screens.*
import com.securevault.viewmodel.AuthViewModel

@Composable
fun SecureVaultNavHost(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsState()

    //  Определяем стартовый экран. Если настройка не завершена, идём в setup, иначе в profiles.
    // Сам экран profiles будет заблокирован overlay, если authState == Locked.
    val startDestination = remember {
        if (authState is AuthViewModel.AuthState.SetupRequired) "setup" else "profiles"
    }

    //  LockScreen работает как защитный overlay поверх всего NavHost
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable("setup") {
                SetupScreen(
                    onCompleted = {
                        navController.navigate("profiles") {
                            popUpTo("setup") { inclusive = true }
                        }
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
                    onNavigateToSettings = { navController.navigate("settings") },
                    //  Ручная блокировка просто меняет состояние, overlay появляется автоматически
                    onLock = { authViewModel.lock() }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToExport = { navController.navigate("export") },
                    onNavigateToChangePassword = { navController.navigate("change_password") }
                )
            }

            composable(
                route = "vault/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getInt("profileId")
                VaultListScreen(
                    profileId = profileId,
                    onNavigateToEntry = { entryId -> navController.navigate("entry/$entryId?profileId=$profileId") },
                    onNavigateToNewEntry = { navController.navigate("entry/new?profileId=$profileId") },
                    onNavigateToAudit = { navController.navigate("audit/$profileId") },
                    onNavigateToExport = { navController.navigate("export/$profileId") },
                    onNavigateToRotation = { navController.navigate("rotation/$profileId") },
                    onNavigateToRotationJournal = { navController.navigate("rotation_journal/$profileId") },
                    onNavigateToSettings = { navController.navigate("profile_settings/$profileId") },
                    onNavigateToMnemonicGenerator = { navController.navigate("mnemonic_generator/$profileId") },
                    onNavigateToQrScanner = { navController.navigate("qr_scanner/$profileId") },
                    onNavigateToProfiles = {
                        navController.navigate("profiles") {
                            popUpTo("vault/$profileId") { inclusive = true }
                        }
                    },
                    // ✅ Ручная блокировка просто меняет состояние
                    onLock = { authViewModel.lock() }
                )
            }

            composable(
                route = "entry/{entryId}?profileId={profileId}",
                arguments = listOf(
                    navArgument("entryId") { type = NavType.StringType },
                    navArgument("profileId") {
                        type = NavType.IntType
                        defaultValue = -1
                    }
                )
            ) { backStackEntry ->
                val entryId = backStackEntry.arguments?.getString("entryId")
                val rawProfileId = backStackEntry.arguments?.getInt("profileId") ?: -1
                val profileId = rawProfileId.takeIf { it > 0 }
                EntryEditorScreen(
                    id = entryId,
                    profileId = profileId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("export") {
                ExportImportScreen(
                    profileId = null,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "export/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getInt("profileId")
                ExportImportScreen(
                    profileId = profileId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "rotation/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getInt("profileId")
                RotationScreen(
                    profileId = profileId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "rotation_journal/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getInt("profileId")
                RotationJournalScreen(
                    profileId = profileId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "profile_settings/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getInt("profileId")
                ProfileSettingsScreen(
                    profileId = profileId,
                    onBack = { navController.popBackStack() },
                    onNavigateToRotation = { navController.navigate("rotation/$profileId") },
                    onNavigateToRotationJournal = { navController.navigate("rotation_journal/$profileId") },
                    onNavigateToAudit = { navController.navigate("audit/$profileId") },
                    onNavigateToExport = { navController.navigate("export/$profileId") },
                    onNavigateToQrScanner = { navController.navigate("qr_scanner/$profileId") }
                )
            }

            composable(
                route = "mnemonic_generator/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getInt("profileId")
                MnemonicGeneratorScreen(
                    profileId = profileId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "qr_scanner/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getInt("profileId")
                QrScannerScreen(
                    profileId = profileId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "audit/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getInt("profileId")
                AuditScreen(
                    profileId = profileId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("change_password") {
                ChangeMasterPasswordScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }

        //  Если приложение заблокировано, показываем LockScreen поверх всего
        if (authState is AuthViewModel.AuthState.Locked || authState is AuthViewModel.AuthState.BruteForceLocked) {
            LockScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                onUnlocked = {
                    // Ничего не делаем. Изменение состояния на Unlocked автоматически скроет этот overlay
                },
                onSetupRequired = {
                    navController.navigate("setup") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
