=======
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.ui.platform.LocalContext
import com.securevault.security.privacyHost
import com.securevault.security.ScreenPrivacyPolicy
import com.securevault.security.SensitiveContent
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securevault.ui.screens.*
import com.securevault.viewmodel.AuthViewModel

private class SafeNavigationMemory {
    var controller: NavHostController? = null
    var returnRoute = "profiles"
    var epoch = -1L
}

@Composable
fun SecureVaultNavHost(
    navController: NavHostController? = null,
    authViewModel: AuthViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val host = LocalContext.current.privacyHost()
    val epoch = host?.screenPrivacy?.safePageEpoch?.collectAsState()?.value ?: 0L
    val memory = remember { SafeNavigationMemory() }
    if (memory.epoch != epoch || authState != AuthViewModel.AuthState.Unlocked) {
        memory.controller?.currentBackStackEntry?.let { entry ->
            memory.returnRoute = ScreenPrivacyPolicy.safeReturnRoute(entry.destination.route,
                entry.arguments?.getInt("profileId")?.takeIf { it > 0 })
        }
        memory.epoch = epoch
    }

    when (authState) {
        AuthViewModel.AuthState.SetupRequired -> {
            SetupScreen(authViewModel = authViewModel, onCompleted = {})
            return
        }
        AuthViewModel.AuthState.Unlocked -> Unit
        else -> {
            LockScreen(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                onUnlocked = {}, onSetupRequired = {}, viewModel = authViewModel)
            return
        }
    }
    // Новая композиция удаляет все окна паролей и их remember-состояния.
    CompositionLocalProvider(LocalSaveableStateRegistry provides null) {
        key(epoch) {
            val activeController = navController ?: rememberNavController()
            SideEffect { memory.controller = activeController }
            DisposableEffect(activeController) {
                onDispose { if (memory.controller === activeController) memory.controller = null }
            }
            UnlockedVaultNavigation(activeController, authViewModel)
            LaunchedEffect(activeController) {
                val route = memory.returnRoute
                if (route != "profiles") {
                    val parent = route.substringAfter('/', "").toIntOrNull()
                    if (parent != null && !route.startsWith("vault/")) activeController.navigate("vault/$parent")
                    activeController.navigate(route) { launchSingleTop = true }
                }
            }
        }
    }
}

@Composable
private fun UnlockedVaultNavigation(navController: NavHostController, authViewModel: AuthViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "profiles") {
            composable("setup") {
                //  ПЕРЕДАЧА AuthViewModel В SETUP
                SetupScreen(
                    authViewModel = authViewModel,
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
                    onLock = { authViewModel.lock() },
                    authViewModel = authViewModel
                )
            }

            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToExport = { navController.navigate("export") },
                    onNavigateToChangePassword = { navController.navigate("change_password") },
                    onLock = { authViewModel.lock() },
                    authViewModel = authViewModel
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
                    onLock = { authViewModel.lock() },
                    authViewModel = authViewModel
                )
            }

            composable(
                route = "entry/{entryId}?profileId={profileId}",
                arguments = listOf(
                    navArgument("entryId") { type = NavType.StringType },
                    navArgument("profileId") { type = NavType.IntType; defaultValue = -1 }
                )
            ) { backStackEntry ->
                SensitiveContent()
                val entryId = backStackEntry.arguments?.getString("entryId")
                val rawProfileId = backStackEntry.arguments?.getInt("profileId") ?: -1
                val profileId = rawProfileId.takeIf { it > 0 }
                EntryEditorScreen(
                    id = entryId,
                    profileId = profileId,
                    onBack = { navController.popBackStack() },
                    onLock = { authViewModel.lock() },
                    authViewModel = authViewModel
                )
            }

            composable("export") {
                ExportImportScreen(
                    profileId = null,
                    onBack = { navController.popBackStack() },
                    onLock = { authViewModel.lock() }
                )
            }

            composable(
                route = "export/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getInt("profileId")
                ExportImportScreen(
                    profileId = profileId,
                    onBack = { navController.popBackStack() },
                    onLock = { authViewModel.lock() }
                )
            }

            composable(
                route = "rotation/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getInt("profileId")
                RotationScreen(
                    profileId = profileId,
                    onEditEntry = { navController.navigate("entry/$it?profileId=$profileId") },
                    onBack = { navController.popBackStack() },
                    onLock = { authViewModel.lock() }
                )
            }

            composable(
                route = "rotation_journal/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getInt("profileId")
                RotationJournalScreen(
                    profileId = profileId,
                    onBack = { navController.popBackStack() },
                    onLock = { authViewModel.lock() },
                    authViewModel = authViewModel
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
                    onNavigateToQrScanner = { navController.navigate("qr_scanner/$profileId") },
                    onLock = { authViewModel.lock() }
                )
            }

            composable(
                route = "mnemonic_generator/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getInt("profileId")
                MnemonicGeneratorScreen(
                    profileId = profileId,
                    onBack = { navController.popBackStack() },
                    onLock = { authViewModel.lock() },
                    authViewModel = authViewModel
                )
            }

            composable(
                route = "qr_scanner/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                SensitiveContent()
                val profileId = backStackEntry.arguments?.getInt("profileId")
                QrScannerScreen(
                    profileId = profileId,
                    onBack = { navController.popBackStack() },
                    onLock = { authViewModel.lock() },
                    authViewModel = authViewModel
                )
            }

            composable(
                route = "audit/{profileId}",
                arguments = listOf(navArgument("profileId") { type = NavType.IntType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getInt("profileId")
                AuditScreen(
                    profileId = profileId,
                    onEditEntry = { navController.navigate("entry/$it?profileId=$profileId") },
                    onBack = { navController.popBackStack() },
                    onLock = { authViewModel.lock() }
                )
            }

            composable("change_password") {
                SensitiveContent()
                ChangeMasterPasswordScreen(
                    viewModel = authViewModel,
                    onBack = { navController.popBackStack() },
                    onLock = { authViewModel.lock() }
                )
            }
        }

    }
}
