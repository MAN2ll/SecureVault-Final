@file:OptIn(ExperimentalMaterial3Api::class)
package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.ui.components.AmpgGeneratorContent
import com.securevault.ui.components.LockActionButton
import com.securevault.utils.ClipboardHelper
import com.securevault.viewmodel.AuthViewModel
import com.securevault.viewmodel.VaultViewModel

@Composable
fun MnemonicGeneratorScreen(
    profileId: Int?,
    onBack: () -> Unit,
    onLock: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    var clearGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(profileId) { if (profileId != null) viewModel.setCurrentProfile(profileId) }
    LaunchedEffect(authViewModel) {
        authViewModel.clearSensitiveEvent.collect { clearGeneration++ }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("AMPG") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
            actions = { LockActionButton(onLock) })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            key(clearGeneration) {
                AmpgGeneratorContent(initialService = "", profileId = profileId, initialYear = null,
                    actionLabel = "Копировать", onGenerated = { password, _, _, _, _ ->
                        ClipboardHelper.copyWithAutoClear(context, password)
                        android.widget.Toast.makeText(context, "Пароль скопирован", android.widget.Toast.LENGTH_SHORT).show()
                    })
            }
        }
    }
}
