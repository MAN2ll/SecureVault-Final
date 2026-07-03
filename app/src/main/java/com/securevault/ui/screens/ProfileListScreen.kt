@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)

package com.securevault.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Profile
import com.securevault.viewmodel.ProfileViewModel
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    onProfileSelected: (Int) -> Unit,
    onLock: () -> Unit,
    profileViewModel: ProfileViewModel = hiltViewModel(),
    vaultViewModel: VaultViewModel = hiltViewModel()
) {
    val profiles by profileViewModel.profiles.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf<Profile?>(null) }

    LaunchedEffect(Unit) {
        vaultViewModel.setCurrentProfile(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SecureVault", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.Lock, "Заблокировать")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Создать профиль")
            }
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text("Нет профилей", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("Создайте первый профиль", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Создать профиль")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(profiles) { profile ->
                    ProfileCard(
                        profile = profile,
                        onClick = { selectedProfile = profile }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProfileDialog(
            onDismiss = { showCreateDialog = false },
            onCreated = { showCreateDialog = false }
        )
    }

    if (selectedProfile != null) {
        UnlockProfileDialog(
            profile = selectedProfile!!,
            onDismiss = { selectedProfile = null },
            onUnlocked = {
                vaultViewModel.setCurrentProfile(selectedProfile!!.id)
                onProfileSelected(selectedProfile!!.id)
                selectedProfile = null
            }
        )
    }
}

@Composable
private fun ProfileCard(profile: Profile, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(140.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(Icons.Default.Folder, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Text("Нажмите для входа", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


@Composable
private fun CreateProfileDialog(onDismiss: () -> Unit, onCreated: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val viewModel: ProfileViewModel = hiltViewModel()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый профиль", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
           
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "PIN профиля — короткий пароль для локального разделения профилей. Мастер-пароль защищает всё хранилище.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it; error = null }, 
                    label = { Text("Название профиля") }, 
                    modifier = Modifier.fillMaxWidth(), 
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = pin, 
                    onValueChange = { pin = it; error = null }, 
                    label = { Text("PIN профиля (4-8 символов)") }, 
                    visualTransformation = PasswordVisualTransformation(), 
                    modifier = Modifier.fillMaxWidth(), 
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = confirmPin, 
                    onValueChange = { confirmPin = it; error = null }, 
                    label = { Text("Подтвердите PIN") }, 
                    visualTransformation = PasswordVisualTransformation(), 
                    modifier = Modifier.fillMaxWidth(), 
                    singleLine = true, 
                    isError = error != null
                )
                
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    name.isBlank() -> error = "Введите название"
                    pin.length < 4 -> error = "PIN слишком короткий (минимум 4 символа)"
                    pin.length > 8 -> error = "PIN слишком длинный (максимум 8 символов)"
                    pin != confirmPin -> error = "PIN не совпадают"
                    else -> { 
                        viewModel.insert(name, pin)
                        onCreated() 
                    }
                }
            }) { 
                Text("Создать") 
            }
        },
        dismissButton = { 
            TextButton(onDismiss) { 
                Text("Отмена") 
            } 
        }
    )
}


@Composable
private fun UnlockProfileDialog(profile: Profile, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val viewModel: ProfileViewModel = hiltViewModel()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Профиль: ${profile.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Введите PIN профиля", fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin, 
                    onValueChange = { pin = it; error = false }, 
                    label = { Text("PIN") }, 
                    visualTransformation = PasswordVisualTransformation(), 
                    modifier = Modifier.fillMaxWidth(), 
                    singleLine = true, 
                    isError = error
                )
                if (error) {
                    Text("Неверный PIN", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (viewModel.verifyPassword(profile, pin)) {
                    onUnlocked()
                } else {
                    error = true
                }
            }) { 
                Text("Войти") 
            }
        },
        dismissButton = { 
            TextButton(onDismiss) { 
                Text("Отмена") 
            } 
        }
    )
}
