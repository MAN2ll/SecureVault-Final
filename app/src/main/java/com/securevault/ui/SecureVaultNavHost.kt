@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    NavHost(navController = navController, startDestination = "lock") {
        composable("lock") {
            LockScreen(
                onUnlocked = {
                    navController.navigate("profiles") {
                        popUpTo("lock") { inclusive = true }
                    }
                },
                onSetupRequired = {
                    navController.navigate("setup") {
                        popUpTo("lock") { inclusive = true }
                    }
                }
            )
        }

        composable("setup") {
            // ✅ ИСПРАВЛЕНО: onSetupComplete заменён на onCompleted
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
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
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
                onLock = {
                    authViewModel.lock()
                    navController.navigate("lock") {
                        popUpTo("vault/$profileId") { inclusive = true }
                    }
                }
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
            val rawProfileId = backStackEntry.arguments?.getInt
