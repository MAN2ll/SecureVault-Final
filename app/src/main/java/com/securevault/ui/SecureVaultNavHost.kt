package com.securevault.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securevault.ui.screens.*

@Composable
fun SecureVaultNavHost() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "lock") {
        
        // 🔐 Экран блокировки (вход в приложение)
        composable("lock") {
            LockScreen(
                onUnlocked = {
                    navController.navigate("main") { 
                        popUpTo("lock") { inclusive = true } 
                    }
                },
                onBiometricRequest = { /* TODO: реализовать биометрию */ },
                onSetupRequired = {
                    navController.navigate("setup") { 
                        popUpTo("lock") { inclusive = true } 
                    }
                }
            )
        }
        
        // ⚙️ Экран первоначальной настройки мастер-пароля
        composable("setup") {
            SetupScreen(
                onCompleted = {
                    navController.navigate("main") {
                        popUpTo("lock") { inclusive = true }
                    }
                }
            )
        }
        
        // 🏠 Главный экран со списком паролей
        composable("main") {
            VaultListScreen(
                onAdd = { navController.navigate("generator?mode=new") },
                onEdit = { id -> navController.navigate("generator?mode=edit&id=$id") },
                onLock = {
                    navController.navigate("lock") { 
                        popUpTo("main") { inclusive = true } 
                    }
                },
                onExport = { navController.navigate("export") },
                onThemeChange = { /* TODO: показать диалог выбора темы */ },
                onRotation = { navController.navigate("rotation") }
            )
        }
        
        // 🎲 Стандартный генератор паролей (случайный)
        composable(
            route = "generator?mode={mode}&id={id}",
            arguments = listOf(
                navArgument("mode") { defaultValue = "new" },
                navArgument("id") { nullable = true }
            )
        ) { backStackEntry ->
            GeneratorScreen(
                onGenerated = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        
        // 🧠 Мнемонический генератор паролей (по фразе)
        composable("mnemonic_generator") {
            MnemonicGeneratorScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        // 📤 Экран экспорта / импорта данных
        composable("export") {
            ExportImportScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        // 🔄 Экран управления ротацией паролей
        composable("rotation") {
            RotationScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        // 👁️ Экран просмотра деталей пароля (с проверкой мастер-пароля)
        composable(
            route = "password_view/{entryId}",
            arguments = listOf(
                navArgument("entryId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId")
            if (entryId != null) {
                PasswordViewDialog(
                    entryId = entryId,
                    onDismiss = { navController.popBackStack() },
                    onVerified = { /* пароль показан, можно закрыть диалог */ }
                )
            }
        }
    }
}
