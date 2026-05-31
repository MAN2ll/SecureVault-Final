@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.utils.ExportManager
import com.securevault.viewmodel.VaultViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportImportScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    exportManager: ExportManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMessage by remember { mutableStateOf<String?>(null) }
    
    val actualExportManager = exportManager ?: run {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ExportManagerEntryPoint::class.java
        )
        entryPoint.exportManager()
    }
    
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            scope.launch {
                val result = actualExportManager.exportToFile(
                    entries = viewModel.entries.value,
                    uri = it,
                    masterPasswordHash = "hash_placeholder"
                )
                showMessage = when (result) {
                    is ExportManager.ExportResult.Success -> "Экспортировано ${result.count}
