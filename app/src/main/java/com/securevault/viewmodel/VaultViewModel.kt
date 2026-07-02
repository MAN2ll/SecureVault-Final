package com.securevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.VaultRepository
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PasswordReplacement(
    val entryId: String,
    val newPassword: String,
    val newHint: String?,
    val newGenerationType: String,
    val newMnemonicPhraseHint: String? = null,
    val newMnemonicOptionsJson: String? = null
)

data class PasswordShuffleAssignment(
    val targetEntryId: String,
    val sourceEntryId: String,
    val isValid: Boolean,
    val validationMessage: String? = null
)

data class PasswordShuffleResult(
    val success: Boolean,
    val assignments: List<PasswordShuffleAssignment> = emptyList(),
    val errorMessage: String? = null
)

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {

    private val _currentProfileId = MutableStateFlow<Int?>(null)
    val currentProfileId: StateFlow<Int?> = _currentProfileId.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    val entries: StateFlow<List<Entry>> = repository.allEntries
        .combine(_currentProfileId) { list, profileId ->
            if (profileId == null) list else list.filter { it.profileId == profileId }
        }
        .combine(_favoritesOnly) { list, favOnly ->
            if (favOnly) list.filter { it.isFavorite } else list
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rotationEntries: StateFlow<List<Entry>> = repository.allEntries
        .combine(_currentProfileId) { list, profileId ->
            if (profileId == null) list else list.filter { it.profileId == profileId }
        }
        .map { list ->
            list.filter { it.rotationEnabled && it.nextRotationDate != null }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setCurrentProfile(profileId: Int?) { _currentProfileId.value = profileId }
    fun toggleFavoritesOnly() { _favoritesOnly.value = !_favoritesOnly.value }

    fun insert(entry: Entry) = viewModelScope.launch { repository.insert(entry) }
    fun updateEntry(entry: Entry) = viewModelScope.launch { repository.update(entry) }
    fun delete(entry: Entry) = viewModelScope.launch { repository.delete(entry) }
    fun deleteAll() = viewModelScope.launch { repository.deleteAll() }

    fun toggleFavorite(entry: Entry) = viewModelScope.launch {
        repository.update(entry.copy(isFavorite = !entry.isFavorite))
    }

    fun updatePassword(entryId: String, newPassword: String) = viewModelScope.launch {
        val entry = repository.getById(entryId) ?: return@launch
        
        val now = System.currentTimeMillis()
        val newNextRotationDate = if (entry.rotationEnabled) {
            now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
        } else {
            null
        }
        
        val fingerprint = PasswordValidator.buildPasswordFingerprint(newPassword)
        
        val updated = entry.addToPasswordHistory(entry.password, entry.generationType).copy(
            encryptedPassword = CryptoUtils.encrypt(newPassword),
            passwordFingerprint = fingerprint,
            lastChanged = now,
            nextRotationDate = newNextRotationDate
        )
        repository.update(updated)
    }

    fun updateRotationSettings(entryId: String, enabled: Boolean, periodMonths: Int) = viewModelScope.launch {
        val entry = repository.getById(entryId) ?: return@launch
        
        val now = System.currentTimeMillis()
        val newNextRotationDate = if (enabled) {
            now + (periodMonths * 30L * 24 * 60 * 60 * 1000)
        } else {
            null
        }
        
        val updated = entry.copy(
            rotationEnabled = enabled,
            rotationPeriodMonths = periodMonths,
            nextRotationDate = newNextRotationDate
        )
        repository.update(updated)
    }

    fun replacePassword(
        entryId: String,
        newPassword: String,
        newHint: String?,
        newGenerationType: String,
        newMnemonicPhraseHint: String? = null,
        newMnemonicOptionsJson: String? = null
    ) = viewModelScope.launch {
        val entry = repository.getById(entryId) ?: return@launch
        
        val now = System.currentTimeMillis()
        val newNextRotationDate = if (entry.rotationEnabled) {
            now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
        } else {
            null
        }
        
        val fingerprint = PasswordValidator.buildPasswordFingerprint(newPassword)
        
        val updated = entry.addToPasswordHistory(entry.password, entry.generationType).copy(
            encryptedPassword = CryptoUtils.encrypt(newPassword),
            textHint = newHint,
            generationType = newGenerationType,
            passwordFingerprint = fingerprint,
            mnemonicPhraseHint = newMnemonicPhraseHint,
            mnemonicOptionsJson = newMnemonicOptionsJson,
            lastChanged = now,
            nextRotationDate = newNextRotationDate
        )
        repository.update(updated)
    }

    fun bulkReplacePasswords(replacements: List<PasswordReplacement>) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        
        replacements.forEach { replacement ->
            val entry = repository.getById(replacement.entryId) ?: return@forEach
            
            val newNextRotationDate = if (entry.rotationEnabled) {
                now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
            } else {
                null
            }
            
            val fingerprint = PasswordValidator.buildPasswordFingerprint(replacement.newPassword)
            
            val updated = entry.addToPasswordHistory(entry.password, entry.generationType).copy(
                encryptedPassword = CryptoUtils.encrypt(replacement.newPassword),
                textHint = replacement.newHint,
                generationType = replacement.newGenerationType,
                passwordFingerprint = fingerprint,
                mnemonicPhraseHint = replacement.newMnemonicPhraseHint,
                mnemonicOptionsJson = replacement.newMnemonicOptionsJson,
                lastChanged = now,
                nextRotationDate = newNextRotationDate
            )
            repository.update(updated)
        }
    }

    // ✅ НОВЫЕ МЕТОДЫ для перемешивания паролей

    fun buildPasswordShufflePlan(entryIds: List<String>): PasswordShuffleResult = runBlocking {
        if (entryIds.size < 2) {
            return@runBlocking PasswordShuffleResult(
                success = false,
                errorMessage = "Для перемешивания нужно минимум 2 сервиса"
            )
        }
        
        val entries = entryIds.mapNotNull { repository.getById(it) }
        if (entries.size != entryIds.size) {
            return@runBlocking PasswordShuffleResult(
                success = false,
                errorMessage = "Не все записи найдены"
            )
        }
        
        // Проверяем, что все записи из одного профиля
        val profileIds = entries.map { it.profileId }.toSet()
        if (profileIds.size > 1) {
            return@runBlocking PasswordShuffleResult(
                success = false,
                errorMessage = "Все записи должны быть из одного профиля"
            )
        }
        
        // Пытаемся построить перестановку
        val assignments = tryBuildPermutation(entries)
        
        if (assignments == null) {
            return@runBlocking PasswordShuffleResult(
                success = false,
                errorMessage = "Не удалось безопасно перемешать пароли. Выберите больше сервисов или используйте другой способ ротации."
            )
        }
        
        PasswordShuffleResult(
            success = true,
            assignments = assignments
        )
    }

    private suspend fun tryBuildPermutation(entries: List<Entry>): List<PasswordShuffleAssignment>? {
        val n = entries.size
        val sourceIndices = (0 until n).toMutableList()
        
        // Пробуем несколько вариантов перестановки
        for (attempt in 0 until 100) {
            sourceIndices.shuffle()
            
            val assignments = mutableListOf<PasswordShuffleAssignment>()
            var valid = true
            
            for (i in 0 until n) {
                val targetEntry = entries[i]
                val sourceIndex = sourceIndices[i]
                val sourceEntry = entries[sourceIndex]
                
                // Не должен получать свой пароль
                if (targetEntry.id == sourceEntry.id) {
                    valid = false
                    break
                }
                
                val sourcePassword = withContext(Dispatchers.Default) {
                    try {
                        sourceEntry.password
                    } catch (e: Exception) {
                        null
                    }
                } ?: run {
                    valid = false
                    break
                }
                
                // Проверка уникальных символов
                if (PasswordValidator.hasDuplicateCharacters(sourcePassword)) {
                    valid = false
                    break
                }
                
                // Проверка повтора пароля
                if (PasswordValidator.wasPasswordUsedForEntry(targetEntry, sourcePassword)) {
                    valid = false
                    break
                }
                
                // Проверка 60% уникальности
                val lastHistory = targetEntry.getPasswordHistory().firstOrNull()
                if (lastHistory?.encryptedOldPassword != null) {
                    try {
                        val oldPassword = CryptoUtils.decrypt(lastHistory.encryptedOldPassword)
                        if (!PasswordValidator.isAtLeast60PercentUnique(oldPassword, sourcePassword)) {
                            valid = false
                            break
                        }
                    } catch (e: Exception) {
                        // Пропускаем проверку
                    }
                }
                
                assignments.add(
                    PasswordShuffleAssignment(
                        targetEntryId = targetEntry.id,
                        sourceEntryId = sourceEntry.id,
                        isValid = true
                    )
                )
            }
            
            if (valid && assignments.size == n) {
                return assignments
            }
        }
        
        return null
    }

    fun validatePasswordShuffleAssignment(
        targetEntryId: String,
        sourceEntryId: String,
        currentAssignments: List<PasswordShuffleAssignment>
    ): ValidationResult {
        if (targetEntryId == sourceEntryId) {
            return ValidationResult(false, "Сервис не может получить свой же пароль")
        }
        
        // Проверяем, что source не используется дважды
        val usedCount = currentAssignments.count { it.sourceEntryId == sourceEntryId && it.targetEntryId != targetEntryId }
        if (usedCount > 0) {
            return ValidationResult(false, "Этот пароль уже назначен другому сервису")
        }
        
        return ValidationResult(true)
    }

    fun applyPasswordShuffle(assignments: List<PasswordShuffleAssignment>): PasswordShuffleResult = runBlocking {
        val now = System.currentTimeMillis()
        
        // Получаем все записи
        val sourceEntries = assignments.mapNotNull { repository.getById(it.sourceEntryId) }
        val targetEntries = assignments.mapNotNull { repository.getById(it.targetEntryId) }
        
        if (sourceEntries.size != assignments.size || targetEntries.size != assignments.size) {
            return@runBlocking PasswordShuffleResult(
                success = false,
                errorMessage = "Не все записи найдены"
            )
        }
        
        val sourcePasswords = sourceEntries.associate { entry ->
            entry.id to try {
                withContext(Dispatchers.Default) { entry.password }
            } catch (e: Exception) {
                null
            }
        }
        
        for (assignment in assignments) {
            val targetEntry = repository.getById(assignment.targetEntryId) ?: continue
            val sourcePassword = sourcePasswords[assignment.sourceEntryId] ?: continue
            val sourceEntry = sourceEntries.find { it.id == assignment.sourceEntryId } ?: continue
            
            val fingerprint = PasswordValidator.buildPasswordFingerprint(sourcePassword)
            val newNextRotationDate = if (targetEntry.rotationEnabled) {
                now + (targetEntry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
            } else {
                null
            }
            
            val updated = targetEntry.addToPasswordHistory(
                oldPassword = targetEntry.password,
                generationType = "shuffled",
                relatedService = sourceEntry.service,
                relatedEntryId = sourceEntry.id
            ).copy(
                encryptedPassword = CryptoUtils.encrypt(sourcePassword),
                passwordFingerprint = fingerprint,
                generationType = "shuffled",
                lastChanged = now,
                nextRotationDate = newNextRotationDate
            )
            repository.update(updated)
        }
        
        PasswordShuffleResult(success = true, assignments = assignments)
    }

    // ✅ Поиск записей, использующих такой же пароль
    suspend fun findEntriesUsingPasswordFingerprint(profileId: Int, fingerprint: String): List<Entry> {
        return withContext(Dispatchers.IO) {
            repository.allEntries.firstOrNull()
                ?.filter { it.profileId == profileId }
                ?.filter { it.passwordFingerprint == fingerprint }
                ?: emptyList()
        }
    }
}

// Хелпер для runBlocking
private fun <T> runBlocking(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking { block() }
}
