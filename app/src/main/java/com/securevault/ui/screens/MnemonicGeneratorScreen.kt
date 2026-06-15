@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicGeneratorScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    var phrase by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(Profile.PERSONAL) }
    var length by remember { mutableIntStateOf(12) }
    var useUpper by remember { mutableStateOf(false) }
    var useDigits by remember { mutableStateOf(false) }
    var useSpecial by remember { mutableStateOf(false) }
    var generatedPassword by remember { mutableStateOf("") }
    var textHint by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf<String?>(null) }

    fun generatePassword() {
        if (phrase.isBlank()) {
            generatedPassword = ""
            return
        }
        
        val consonants = "бвгджзйклмнпрстфхцчшщbcdfghjklmnpqrstvwxyz"
        val extracted = StringBuilder()
        for (ch in phrase) {
            if (ch.lowercaseChar().toString() in consonants) {
                extracted.append(ch.lowercaseChar())
            }
        }
        
        val map = mapOf(
            'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ж' to "zh",
            'з' to "z", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
            'н' to "n", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch"
        )
        
        var base = StringBuilder()
        for (ch in extracted.toString()) {
            if (ch in map) {
                base.append(map[ch])
            } else {
                base.append(ch)
            }
        }
        
        if (!useUpper) base = StringBuilder(base.toString().lowercase())
        if (useDigits) base.append((10..99).random().toString())
        if (useSpecial) base.append(listOf("!", "@", "#", "$").random())
        
        val safeLength = length.coerceIn(8, 20)
        generatedPassword = if (base.length > safeLength) {
            base.toString().take(safeLength)
        } else if (base.length < safeLength) {
            var result = base.toString()
            while (result.length < safeLength) {
                result += "abcdefghijklmnopqrstuvwxyz".random()
            }
            result
        } else {
            base.toString()
        }
    }

    LaunchedEffect(phrase, length, useUpper, useDigits, useSpecial) {
        if (phrase.isNotBlank()) generatePassword()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Генератор из фразы", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it },
                label = { Text("Ваша фраза") },
                placeholder = { Text("например: Мой кот любит молоко") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = service,
                onValueChange = { service = it },
                label = { Text("Сервис") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Логин") },
                modifier = Modifier.fillMaxWidth()
            )

            // Профиль
            var expandedProfile by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedProfile,
                onExpandedChange = { expandedProfile = !expandedProfile }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = profile.label,
                    onValueChange = {},
                    label = { Text("Профиль") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProfile) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
               
