package com.securevault.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.securevault.data.BackupData
import com.securevault.data.Entry
import com.securevault.data.VaultRepository
import com.securevault.utils.BackupManager
import com.securevault.utils.CryptoUtils
import com.securevault.utils.ImportMode
import com.securevault.utils.PasswordValidator
import com.securevault.utils.RotationCheckWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class PasswordOperationResult {
    object Success : PasswordOperationResult()
    data class Error(val message: String) : PasswordOperationResult()
}

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val appContext: Application
) : AndroidViewModel(appContext) {

    private val _currentProfileId = MutableStateFlow<Int?>(null)
    val currentProfileId: StateFlow<Int?> = _currentProfileId.asStateFlow()

    val entries: StateFlow<List<Entry>> = repository.allEntries
        .combine(_currentProfileId) { all, pid ->
            if (pid == null) emptyList() else all.filter { it.profileId == pid }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _rotationEntries = MutableStateFlow<List<Entry>>(emptyList())
    val rotationEntries: StateFlow<List<Entry>> = _rotationEntries.asStateFlow()
    val favoritesOnly = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            _currentProfileId.collect { pid ->
                if (pid != null) {
                    _rotationEntries.value = repository.getEntriesWithRotation().filter { it.profileId == pid }
                } else {
                    _rotationEntries.value = emptyList()
                }
            }
        }
    }

    fun setCurrentProfile(profileId: Int?) { _currentProfileId.value = profileId }
    fun toggleFavoritesOnly() { favoritesOnly.value = !favoritesOnly.value }
    fun toggleFavorite(entry: Entry) = viewModelScope.launch { repository.update(entry.copy(isFavorite = !entry.isFavorite)) }
    fun insert(entry: Entry) = viewModelScope.launch { repository.insert(entry) }
    fun findEntryById(entryId: String): Entry? = repository.getByIdBlocking(entryId)

    fun insertEntry(entry: Entry, onResult: (PasswordOperationResult) -> Unit) = viewModelScope.launch {
        try { repository.insert(entry); onResult(PasswordOperationResult.Success) }
        catch (e: Exception) { onResult(PasswordOperationResult.Error(e.message ?: "Ошибка")) }
    }

    fun updateEntry(entry: Entry, onResult: (PasswordOperationResult) -> Unit) = viewModelScope.launch {
        try { repository.update(entry); onResult(PasswordOperationResult.Success) }
        catch (e: Exception) { onResult(PasswordOperationResult.Error(e.message ?: "Ошибка")) }
    }

    fun deleteEntry(entryId: String, expectedProfileId: Int, onResult: (PasswordOperationResult) -> Unit) = viewModelScope.launch {
        try {
            val entry = repository.getById(entryId) ?: run { onResult(PasswordOperationResult.Error("Не найдена")); return@launch }
            if (entry.profileId != expectedProfileId) { onResult(PasswordOperationResult.Error("Другой профиль")); return@launch }
            repository.delete(entry)
            onResult(PasswordOperationResult.Success)
        } catch (e: Exception) { onResult(PasswordOperationResult.Error(e.message ?: "Ошибка")) }
    }

    fun deleteEntries(entryIds: List<String>, expectedProfileId: Int, onResult: (PasswordOperationResult) -> Unit) = viewModelScope.launch {
        try {
            for (id in entryIds) {
                val entry = repository.getById(id)
                if (entry != null && entry.profileId == expectedProfileId) repository.delete(entry)
            }
            onResult(PasswordOperationResult.Success)
        } catch (e: Exception) { onResult(PasswordOperationResult.Error(e.message ?: "Ошибка")) }
    }

    fun deleteAllEntriesInProfile(profileId: Int, onResult: (PasswordOperationResult) -> Unit) = viewModelScope.launch {
        try { repository.deleteEntriesByProfileId(profileId); onResult(PasswordOperationResult.Success) }
        catch (e: Exception) { onResult(PasswordOperationResult.Error(e.message ?: "Ошибка")) }
    }

    fun deleteAll() {
        val pid = _currentProfileId.value ?: return
        viewModelScope.launch { repository.deleteEntriesByProfileId(pid) }
    }

    // Очистка mnemonic при random/manual
    fun replacePassword(
        entryId: String, newPassword: String, newHint: String?,
        newGenerationType: String, newMnemonicPhraseHint: String?,
        newMnemonicOptionsJson: String?, onResult: ((PasswordOperationResult) -> Unit)? = null
    ) = viewModelScope.launch {
        try {
            val entry = repository.getById(entryId) ?: run {
                onResult?.invoke(PasswordOperationResult.Error("Не найдена")); return@launch
            }
            val validation = PasswordValidator.validateNewPasswordForEntry(entry, newPassword, appContext)
            if (!validation.isValid) {
                onResult?.invoke(PasswordOperationResult.Error(validation.errorMessage ?: "Ошибка")); return@launch
            }
            val now = System.currentTimeMillis()
            val encryptedPwd = CryptoUtils.encrypt(newPassword)
            val newFp = PasswordValidator.buildPasswordFingerprint(newPassword, appContext)
            val oldFp = PasswordValidator.buildPasswordFingerprint(entry.password, appContext)

            val updatedHint = when (newGenerationType) {
                "mnemonic", "manual" -> newHint
                "random" -> null
                else -> newHint
            }
            val updatedMnemonicPhrase = if (newGenerationType == "mnemonic") newMnemonicPhraseHint else null
            val updatedMnemonicOptions = if (newGenerationType == "mnemonic") newMnemonicOptionsJson else null

            val updated = entry.addToPasswordHistory(entry.password, entry.generationType, oldFp).copy(
                encryptedPassword = encryptedPwd, passwordFingerprint = newFp, lastChanged = now,
                generationType = newGenerationType,
                textHint = updatedHint,
                mnemonicPhraseHint = updatedMnemonicPhrase,
                mnemonicOptionsJson = updatedMnemonicOptions,
                nextRotationDate = if (entry.rotationEnabled) now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000) else null
            )
            repository.update(updated)
            onResult?.invoke(PasswordOperationResult.Success)
        } catch (e: Exception) { onResult?.invoke(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
    }

    //  Валидация всех replacements перед применением
    fun bulkReplacePasswords(replacements: List<BulkPasswordReplacement>, onResult: ((PasswordOperationResult) -> Unit)? = null) = viewModelScope.launch {
        try {
            // 1. Сначала валидируем ВСЕ замены
            val validationErrors = mutableListOf<String>()
            val validatedReplacements = mutableListOf<Pair<Entry, BulkPasswordReplacement>>()

            for (replacement in replacements) {
                val entry = repository.getById(replacement.entryId)
                if (entry == null) {
                    validationErrors.add("Запись не найдена: ${replacement.entryId}")
                    continue
                }

                val validation = PasswordValidator.validateNewPasswordForEntry(entry, replacement.newPassword, appContext)
                if (!validation.isValid) {
                    validationErrors.add("${entry.service} — ${validation.errorMessage ?: "ошибка валидации"}")
                    continue
                }

                validatedReplacements.add(Pair(entry, replacement))
            }

            // 2. Если есть ошибки — отменяем всю операцию
            if (validationErrors.isNotEmpty()) {
                val errorMessage = buildString {
                    append("Не удалось выполнить массовую ротацию:\n")
                    validationErrors.forEach { error ->
                        append("• $error\n")
                    }
                }
                onResult?.invoke(PasswordOperationResult.Error(errorMessage))
                return@launch
            }

            // 3. Если все валидно — применяем изменения
            for ((entry, replacement) in validatedReplacements) {
                val now = System.currentTimeMillis()
                val encryptedPwd = CryptoUtils.encrypt(replacement.newPassword)
                val newFp = PasswordValidator.buildPasswordFingerprint(replacement.newPassword, appContext)
                val oldFp = PasswordValidator.buildPasswordFingerprint(entry.password, appContext)

                val updatedHint = if (replacement.generationType == "random") null else replacement.textHint
                val updatedMnemonicPhrase = if (replacement.generationType == "mnemonic") replacement.mnemonicPhraseHint else null
                val updatedMnemonicOptions = if (replacement.generationType == "mnemonic") replacement.mnemonicOptionsJson else null

                val updated = entry.addToPasswordHistory(entry.password, entry.generationType, oldFp).copy(
                    encryptedPassword = encryptedPwd, passwordFingerprint = newFp, lastChanged = now,
                    generationType = replacement.generationType,
                    textHint = updatedHint,
                    mnemonicPhraseHint = updatedMnemonicPhrase,
                    mnemonicOptionsJson = updatedMnemonicOptions,
                    nextRotationDate = if (entry.rotationEnabled) now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000) else null
                )
                repository.update(updated)
            }

            onResult?.invoke(PasswordOperationResult.Success)
        } catch (e: Exception) { onResult?.invoke(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
    }

    fun shufflePasswords(entries: List<Entry>, onResult: (PasswordOperationResult) -> Unit) = viewModelScope.launch {
        try {
            if (entries.size < 2) {
                onResult(PasswordOperationResult.Error("Нужно минимум 2 записи")); return@launch
            }
            val decrypted = entries.map {
                try { it.password } catch (e: Exception) {
                    throw Exception("Не удалось расшифровать '${it.service}': ${e.message}")
                }
            }
            val now = System.currentTimeMillis()
            val updates = mutableMapOf<String, Pair<String, String>>()
            for (i in entries.indices) {
                val target = entries[i]
                val newPwd = decrypted[(i + 1) % entries.size]
                if (newPwd == target.password) continue
                val validation = PasswordValidator.validateNewPasswordForEntry(target, newPwd, appContext)
                if (!validation.isValid) {
                    onResult(PasswordOperationResult.Error("Невозможно: '${target.service}' -> ${validation.errorMessage}"))
                    return@launch
                }
                val enc = CryptoUtils.encrypt(newPwd)
                val fp = PasswordValidator.buildPasswordFingerprint(newPwd, appContext)
                updates[target.id] = Pair(enc, fp)
            }
            for (entry in entries) {
                val upd = updates[entry.id] ?: continue
                val oldFp = PasswordValidator.buildPasswordFingerprint(entry.password, appContext)
                val updated = entry.addToPasswordHistory(entry.password, entry.generationType, oldFp).copy(
                    encryptedPassword = upd.first, passwordFingerprint = upd.second, lastChanged = now,
                    generationType = "shuffled",
                    textHint = null,
                    mnemonicPhraseHint = null,
                    mnemonicOptionsJson = null,
                    nextRotationDate = if (entry.rotationEnabled) now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000) else null
                )
                repository.update(updated)
            }
            onResult(PasswordOperationResult.Success)
        } catch (e: Exception) { onResult(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
    }

    fun buildPasswordShufflePlan(selectedIds: List<String>, onResult: (PasswordShufflePlanResult) -> Unit) = viewModelScope.launch {
        try {
            if (selectedIds.size < 2) { onResult(PasswordShufflePlanResult(false, emptyList(), "Нужно минимум 2")); return@launch }
            val entries = selectedIds.mapNotNull { repository.getById(it) }
            if (entries.size != selectedIds.size) { onResult(PasswordShufflePlanResult(false, emptyList(), "Не все найдены")); return@launch }
            val pid = entries.first().profileId
            if (entries.any { it.profileId != pid }) { onResult(PasswordShufflePlanResult(false, emptyList(), "Разные профили")); return@launch }
            val assignments = mutableListOf<PasswordShuffleAssignment>()
            val used = mutableSetOf<String>()
            for (target in entries) {
                val available = entries.filter { it.id != target.id && it.id !in used }
                if (available.isEmpty()) { onResult(PasswordShufflePlanResult(false, emptyList(), "Не удалось")); return@launch }
                val source = available.first(); used.add(source.id)
                val v = validatePasswordShuffleAssignment(target.id, source.id, assignments)
                assignments.add(PasswordShuffleAssignment(target.id, source.id, v.isValid, v.errorMessage))
            }
            onResult(PasswordShufflePlanResult(true, assignments, null))
        } catch (e: Exception) { onResult(PasswordShufflePlanResult(false, emptyList(), e.message ?: "Ошибка")) }
    }

    fun validatePasswordShuffleAssignment(targetId: String, sourceId: String, current: List<PasswordShuffleAssignment>): PasswordValidator.ValidationResult {
        if (targetId == sourceId) return PasswordValidator.ValidationResult(false, "Сервис не может получить свой пароль")
        if (current.any { it.sourceEntryId == sourceId && it.targetEntryId != targetId })
            return PasswordValidator.ValidationResult(false, "Пароль уже назначен")
        return PasswordValidator.ValidationResult(true)
    }

    fun applyPasswordShuffle(assignments: List<PasswordShuffleAssignment>, onResult: (PasswordShufflePlanResult) -> Unit) = viewModelScope.launch {
        try {
            for (a in assignments) {
                if (!a.isValid) { onResult(PasswordShufflePlanResult(false, emptyList(), "Несовместимо")); return@launch }
                val src = repository.getById(a.sourceEntryId) ?: run { onResult(PasswordShufflePlanResult(false, emptyList(), "Не найдена")); return@launch }
                val tgt = repository.getById(a.targetEntryId) ?: run { onResult(PasswordShufflePlanResult(false, emptyList(), "Не найдена")); return@launch }
                val newPwd = src.password
                val v = PasswordValidator.validateNewPasswordForEntry(tgt, newPwd, appContext)
                if (!v.isValid) { onResult(PasswordShufflePlanResult(false, emptyList(), v.errorMessage ?: "Ошибка")); return@launch }
                val now = System.currentTimeMillis()
                val enc = CryptoUtils.encrypt(newPwd)
                val newFp = PasswordValidator.buildPasswordFingerprint(newPwd, appContext)
                val oldFp = PasswordValidator.buildPasswordFingerprint(tgt.password, appContext)
                val updated = tgt.addToPasswordHistory(tgt.password, tgt.generationType, oldFp).copy(
                    encryptedPassword = enc, passwordFingerprint = newFp, lastChanged = now,
                    generationType = "shuffled",
                    textHint = null,
                    mnemonicPhraseHint = null,
                    mnemonicOptionsJson = null,
                    nextRotationDate = if (tgt.rotationEnabled) now + (tgt.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000) else null
                )
                repository.update(updated)
            }
            onResult(PasswordShufflePlanResult(true, emptyList(), null))
        } catch (e: Exception) { onResult(PasswordShufflePlanResult(false, emptyList(), e.message ?: "Ошибка")) }
    }

    // Экспорт всех профилей для backup
    suspend fun exportAllProfiles(): BackupData {
        return BackupManager.exportAllProfiles(repository)
    }

    //  Передаём appContext в BackupManager
    suspend fun importBackup(
        backupData: BackupData,
        mode: ImportMode,
        newPin: String
    ): com.securevault.utils.ImportResult {
        return BackupManager.importBackup(repository, backupData, mode, newPin, appContext)
    }

    // Планирование фоновой проверки ротации
    fun scheduleRotationCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<RotationCheckWorker>(
            1, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "rotation_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

data class PasswordShuffleAssignment(
    val targetEntryId: String, val sourceEntryId: String,
    val isValid: Boolean, val validationMessage: String?
)

data class PasswordShufflePlanResult(
    val success: Boolean, val assignments: List<PasswordShuffleAssignment>,
    val errorMessage: String?
)

// data class для bulk replacement
data class BulkPasswordReplacement(
    val entryId: String,
    val newPassword: String,
    val generationType: String,
    val textHint: String? = null,
    val mnemonicPhraseHint: String? = null,
    val mnemonicOptionsJson: String? = null
)
