@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicGeneratorScreen(
    profileId: Int?,
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    LaunchedEffect(profileId) {
        if (profileId != null) viewModel.setCurrentProfile(profileId)
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentProfileId by viewModel.currentProfileId.collectAsState()
    val effectiveProfileId = profileId ?: currentProfileId

    var phrase by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }
    var includeLeet by remember { mutableStateOf(true) }
    var splitMode by remember { mutableStateOf(MnemonicPasswordGenerator.SplitMode.SINGLE_USER) }
    var targetLength by remember { mutableIntStateOf(16) }
    
    var variantPages by remember { mutableStateOf<List<List<MnemonicPasswordGenerator.GenerationResult>>>(emptyList()) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var nextOffset by remember { mutableIntStateOf(0) }
    var isGenerating by remember { mutableStateOf(false) }
    var noMoreVariants by remember { mutableStateOf(false) }
    var selectedVariantIndex by remember { mutableIntStateOf(-1) }
    var showExplanation by remember { mutableStateOf<MnemonicPasswordGenerator.GenerationResult?>(null) }

    fun loadNextPage() {
        if (isGenerating || noMoreVariants) return
        isGenerating = true
        
        //  ЗАПРАШИВАЕМ РОВНО 3 ВАЛИДНЫХ ВАРИАНТА
        val options = MnemonicPasswordGenerator.GenerationOptions(
            phrase = phrase,
            serviceName = serviceName,
            username = "", 
            profileId = effectiveProfileId,
            targetLength = if (splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS) {
                when { targetLength <= 16 -> 16; targetLength <= 18 -> 18; else -> 20 }
            } else { targetLength },
            includeLeet = includeLeet,
            variantOffset = nextOffset,
            enforceUniqueChars = true,
            splitMode = splitMode
        )
        
        val newVariants = MnemonicPasswordGenerator.generateVariants(options, count = 3)
        
        if (newVariants.isNotEmpty()) {
            variantPages = variantPages + listOf(newVariants)
            currentPageIndex = variantPages.size - 1
            nextOffset = options.variantOffset + 150 // Сдвигаем offset на максимум попыток
            noMoreVariants = newVariants.size < 3
        } else {
            noMoreVariants = true
        }
        isGenerating = false
        selectedVariantIndex = -1
    }

    fun loadPreviousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            selectedVariantIndex = -1
        }
    }

    LaunchedEffect(phrase, serviceName, includeLeet, splitMode, targetLength) {
        variantPages = emptyList()
        currentPageIndex = 0
        nextOffset = 0
        noMoreVariants = false
        selectedVariantIndex = -1
        if (phrase.isNotBlank()) loadNextPage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AMPG генератор", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Режим генерации", fontWeight = FontWeight.Bold, fontSize = 
