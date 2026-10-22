@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.ui.components.*
import com.securevault.utils.*
import com.securevault.viewmodel.AuthViewModel
import com.securevault.viewmodel.VaultViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun EntryEditorScreen(id: String? = null, profileId: Int?, onBack: () -> Unit,
    onLock: () -> Unit = {}, authViewModel: AuthViewModel,
    viewModel: VaultViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles by viewModel.profiles.collectAsState()
    val edit = id != null && id != "new"
    var original by remember(id) { mutableStateOf<Entry?>(null) }
    var originalPassword by remember(id) { mutableStateOf<String?>(null) }
    var draft by remember(id) { mutableStateOf<String?>(null) }
    var service by remember(id) { mutableStateOf("") }
    var username by remember(id) { mutableStateOf("") }
    var url by remember(id) { mutableStateOf("") }
    var notes by remember(id) { mutableStateOf("") }
    var tags by remember(id) { mutableStateOf("") }
    var favorite by remember(id) { mutableStateOf(false) }
    var rotation by remember(id) { mutableStateOf(false) }
    var months by remember(id) { mutableIntStateOf(6) }
    var mode by remember(id) { mutableStateOf(AccessMode.INHERIT) }
    var type by remember(id) { mutableStateOf("manual") }
    var hint by remember(id) { mutableStateOf<String?>(null) }
    var mnemonicHint by remember(id) { mutableStateOf<String?>(null) }
    var mnemonicJson by remember(id) { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(edit) }
    var saving by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var generator by remember { mutableStateOf(false) }
    var accessDialog by remember { mutableStateOf(false) }
    var biometric by remember { mutableStateOf(false) }
    var accessConfirmed by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val effectiveProfileId = original?.profileId ?: profileId
    val profile = profiles.find { it.id == effectiveProfileId }

    LaunchedEffect(id, profileId) {
        try {
            if (edit) {
                val item = viewModel.getEntryById(requireNotNull(id)) ?: throw IllegalStateException("Запись не найдена")
                require(profileId == null || item.profileId == profileId) { "Запись другого профиля" }
                original = item
                service = item.service; username = item.username; url = item.url.orEmpty()
                notes = item.notes.orEmpty(); tags = item.tagsCsv; favorite = item.isFavorite
                rotation = item.rotationEnabled; months = item.rotationPeriodMonths
                mode = AccessMode.fromString(item.passwordAccessMode)
                type = item.generationType; hint = item.textHint
                mnemonicHint = item.mnemonicPhraseHint; mnemonicJson = item.mnemonicOptionsJson
            } else require(profileId != null && profileId > 0) { "Профиль не выбран" }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { loadError = e.message ?: "Ошибка загрузки"
        } finally { loading = false }
    }
    LaunchedEffect(authViewModel) {
        authViewModel.clearSensitiveEvent.collect {
            originalPassword = null; draft = null; visible = false
            generator = false; accessDialog = false; pending = null; accessConfirmed = false
        }
    }
    fun requestAccess(action: () -> Unit) {
        error = null
        val base = original
        if (!edit) { action(); return }
        val owner = profile ?: run { error = "Профиль записи не найден"; return }
        when (resolveAccess(base ?: return, owner)) {
            AccessResult.Granted -> { accessConfirmed = true; action() }
            AccessResult.PinNotSet -> error = "Сначала задайте PIN профиля"
            else -> {
                biometric = resolveAccess(base, owner) is AccessResult.BiometricOrPin
                pending = action; accessDialog = true
            }
        }
    }
    fun loadPassword(after: () -> Unit) {
        fun decrypt() {
            try {
                val old = originalPassword ?: CryptoUtils.decrypt(requireNotNull(original).encryptedPassword)
                originalPassword = old
                if (draft == null) draft = old
                after()
            } catch (e: Exception) { error = "Не удалось расшифровать пароль: ${e.message}" }
        }
        if (!edit || originalPassword != null) after() else requestAccess { decrypt() }
    }
    fun save() {
        if (saving) return
        if (service.isBlank()) { error = "Укажите сервис"; return }
        if (edit && mode != AccessMode.fromString(original?.passwordAccessMode) && !accessConfirmed) {
            requestAccess { accessConfirmed = true; save() }
            return
        }
        saving = true
        scope.launch {
            try {
                val owner = requireNotNull(effectiveProfileId) { "Профиль не выбран" }
                if (mode == AccessMode.PIN_REQUIRED || mode == AccessMode.BIOMETRIC_OR_PIN) {
                    require(profile != null && profile.passwordHash.isNotBlank() && profile.passwordSalt.isNotBlank()) {
                        "Для выбранного режима сначала задайте PIN профиля"
                    }
                }
                val finalTags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }.joinToString(",")
                val base = original
                val updated = if (base == null) {
                    val pwd = draft.orEmpty()
                    require(pwd.isNotBlank()) { "Введите пароль" }
                    val validation = PasswordValidator.validateUniqueCharacters(pwd)
                    require(validation.isValid) { validation.errorMessage.orEmpty() }
                    Entry.create(service.trim(), username, pwd, owner,
                        PasswordValidator.buildPasswordFingerprint(pwd, context),
                        url.ifBlank { null }, notes.ifBlank { null }, hint, rotation, months,
                        favorite, type, mnemonicHint, mnemonicJson, mode.value, finalTags)
                } else {
                    val changed = draft != null && draft != originalPassword
                    require(!changed || originalPassword != null) { "Сначала подтвердите доступ к паролю" }
                    val passwordEntry = if (changed) EntryChanges.replace(base, draft.orEmpty(), type,
                        context, hint, mnemonicHint, mnemonicJson) else base
                    passwordEntry.copy(service = service.trim(), username = username,
                        url = url.ifBlank { null }, notes = notes.ifBlank { null }, tagsCsv = finalTags,
                        isFavorite = favorite, rotationEnabled = rotation, rotationPeriodMonths = months,
                        passwordAccessMode = if (mode == AccessMode.fromString(base.passwordAccessMode))
                            base.passwordAccessMode else mode.value,
                        nextRotationDate = EntryChanges.nextRotationDate(base, rotation, months,
                            changed, System.currentTimeMillis()))
                }
                viewModel.saveEntry(updated, base)
                onBack()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "Ошибка сохранения"
            } finally { saving = false }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (edit) "Редактирование" else "Новая запись") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
            actions = { LockActionButton(onLock) })
    }) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            loadError != null -> Column(Modifier.padding(padding).padding(16.dp)) {
                Text(loadError!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onBack) { Text("Назад") }
            }
            else -> Column(Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(service, { service = it }, label = { Text("Сервис *") }, enabled = !saving,
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(username, { username = it }, label = { Text("Логин / Email") }, enabled = !saving,
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.orEmpty(), {
                    draft = it; type = "manual"; hint = null; mnemonicHint = null; mnemonicJson = null
                }, label = { Text("Пароль *") }, enabled = !saving,
                    readOnly = edit && originalPassword == null, singleLine = true,
                    placeholder = { if (edit && originalPassword == null) Text("••••••••••••") },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { Row {
                        IconButton(onClick = { loadPassword { visible = !visible } }) {
                            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Показать / скрыть")
                        }
                        IconButton(onClick = { loadPassword { visible = false; generator = true } }) {
                            Icon(Icons.Default.AutoAwesome, "Генератор")
                        }
                    } }, modifier = Modifier.fillMaxWidth())
                Text("Ручной ввод или генерация. Просмотр пароля не считается заменой.")
                OutlinedTextField(tags, { tags = it }, label = { Text("Теги через запятую") },
                    enabled = !saving, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text("URL сайта") },
                    enabled = !saving, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Заметки") },
                    enabled = !saving, minLines = 3, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(favorite, { favorite = it }, enabled = !saving); Text("Избранное")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rotation, { rotation = it }, enabled = !saving); Text("Напоминать о смене пароля")
                }
                if (rotation) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 6, 12).forEach { period ->
                        FilterChip(months == period, { months = period }, label = { Text("$period мес.") }, enabled = !saving)
                    }
                }
                Text("Режим доступа к паролю")
                val labels = listOf("Как в профиле", "Без подтверждения", "Только PIN профиля", "Биометрия или PIN")
                AccessMode.values().forEachIndexed { index, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(mode == value, { mode = value }, enabled = !saving); Text(labels[index])
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { save() }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Сохранить")
                }
            }
        }
    }
    if (accessDialog && profile != null) ProfileAccessDialog(profile, biometric,
        onDismiss = { accessDialog = false; pending = null }, onGranted = {
            accessDialog = false; accessConfirmed = true
            val action = pending; pending = null; action?.invoke()
        })
    if (generator) UnifiedPasswordGeneratorDialog(onDismiss = { generator = false },
        onGenerated = { pwd, generatedType, generatedHint, phrase, options ->
            draft = pwd; type = generatedType; hint = generatedHint
            mnemonicHint = phrase; mnemonicJson = options; visible = false; generator = false
        }, initialServiceName = service, profile = profile)
}
