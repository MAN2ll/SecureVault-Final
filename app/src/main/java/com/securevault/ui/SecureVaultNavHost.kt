package com.securevault.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.securevault.ui.screens.*

@Composable
fun SecureVaultNavHost(onThemeChange: () -> Unit) {
    val nav = rememberNavController()
    NavHost(nav, "lock") {
        composable("lock") { LockScreen(onUnlocked = { nav.navigate("main") { popUpTo("lock") { inclusive = true } } }, onSetupRequired = { nav.navigate("setup") { popUpTo("lock") { inclusive = true } } }) }
        composable("setup") { SetupScreen(onCompleted = { nav.navigate("main") { popUpTo("lock") { inclusive = true } } }) }
        composable("main") { VaultListScreen(onAdd = { nav.navigate("generator") }, onThemeChange = onThemeChange, onReminders = { nav.navigate("reminders") }) }
        composable("generator") { GeneratorScreen(onBack = { nav.popBackStack() }) }
        composable("mnemonic") { MnemonicGeneratorScreen(onBack = { nav.popBackStack() }) }
        composable("reminders") { ReminderScreen(onBack = { nav.popBackStack() }) }
    }
}
