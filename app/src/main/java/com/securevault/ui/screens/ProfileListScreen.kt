@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Profile
import com.securevault.security.MasterPasswordHasher
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.ProfileViewModel
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    onProfileSelected: (Int) -> Unit,
    onLock: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToExportImport: () -> Unit,
    profileViewModel: ProfileViewModel = hiltViewModel(),
    vaultViewModel: VaultViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val profiles by profileViewModel.profiles.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf<Profile?>(null) }

    var profileToDelete by remember { mutableStateOf<Profile?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var showMasterPasswordForDelete by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vaultViewModel.setCurrentProfile(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SecureVault", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Общие настройки")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Меню")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Экспорт / импорт") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToExportImport()
                                },
                                leadingIcon = { Icon(Icons.Default.Upload, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Заблокировать") },
                                onClick = {
                                    showMenu = false
                                    onLock()
                                },
                                leadingIcon = { Icon(Icons.Default.Lock, null) }
                            )
                        }
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
                        onClick = { selectedProfile = profile },
                        onDeleteClick = { profileToDelete = profile }
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

    // Проверка наличия записей перед удалением
    if (profileToDelete != null && !showDeleteConfirmDialog && !showMasterPasswordForDelete) {
        LaunchedEffect(profileToDelete) {
            val profile = profileToDelete!!
            profileViewModel.hasEntriesInProfile(profile.id) { hasEntries ->
                if (hasEntries) {
                    operationError = "Нельзя удалить профиль, пока в нём есть пароли. Сначала удалите все пароли профиля."
                    profileToDelete = null
                } else {
                    showMasterPasswordForDelete = true
                }
            }
        }
    }

    if (showMasterPasswordForDelete && profileToDelete != null) {
        MasterPasswordConfirmDialog(
            title = "Подтверждение удаления профиля",
            onConfirmed = {
                showMasterPasswordForDelete = false
                showDeleteConfirmDialog = true
            },
            onDismiss = {
                showMasterPasswordForDelete = false
                profileToDelete = null
            }
        )
    }

    if (showDeleteConfirmDialog && profileToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                profileToDelete = null
            },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить профиль?") },
            text = { Text("Удалить профиль \"${profileToDelete!!.name}\"? Это действие необратимо.") },
            confirmButton = {
                Button(
                    onClick = {
                        val profile = profileToDelete!!
                        profileViewModel.deleteProfile(profile.id) { result ->
                            when (result) {
                                is PasswordOperationResult.Success -> {
                                    showDeleteConfirmDialog = false
                                    profileToDelete = null
                                }
                                is PasswordOperationResult.Error -> {
                                    operationError = result.message
                                    showDeleteConfirmDialog = false
                                    profileToDelete = null
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    profileToDelete = null
                }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (operationError != null) {
        AlertDialog(
            onDismissRequest = { operationError = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Ошибка") },
            text = { Text(operationError ?: "") },
            confirmButton = {
                TextButton(onClick = { operationError = null }) {
                    Text("Понятно")
                }
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Folder, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Меню профиля", modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Удалить профиль", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
                Column {
                    Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                    Text("Нажмите для входа", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
