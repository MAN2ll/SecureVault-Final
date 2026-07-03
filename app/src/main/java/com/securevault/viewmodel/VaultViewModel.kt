package com.securevault.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.VaultRepository
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

sealed class PasswordOperationResult {
    object Success : PasswordOperationResult()
    data class Error(val message: String) : PasswordOperationResult()
}

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository,
    @ApplicationContext private val appContext: Context
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

    fun setCurrentProfile(profileId: Int?) {
        _currentProfileId.value = profileId
        // ✅ Запускаем backfill при смене профиля (один раз)
        backfillFingerprints()
    }

    fun toggleFavoritesOnly() { _favoritesOnly.value = !_favoritesOnly.value }

    fun insert(entry: Entry) = viewModelScope.launch { repository.insert(entry) }
    fun updateEntry(entry: Entry) = viewModelScope.launch { repository.update(entry) }
    fun delete(entry: Entry) = viewModelScope.launch { repository.delete(entry) }
    fun deleteAll() = viewModelScope.launch { repository.deleteAll() }

    fun toggleFavorite(entry: Entry) = viewModelScope.launch {
        repository.update(entry.copy(isFavorite = !entry.isFavorite))
    }

    // ✅ Backfill запускается один раз при setCurrentProfile
    private var backfillDone = mutableSetOf<Int?>()

    fun backfillFingerprints() = viewModelScope.launch {
        val profileId = _currentProfileId.value
        if (profileId in backfillDone) return@launch
        backfillDone.add(profileId)

        val allEntries = repository.allEntries.first()
        for (entry in allEntries) {
            if (entry.passwordFingerprint.isNullOrBlank()) {
                try {
                    val password = withContext(Dispatchers.Default) { entry.password }
                    val fingerprint = PasswordValidator.buildPasswordFingerprint(password, appContext)
                    repository.update(entry.copy(passwordFingerprint = fingerprint))
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun updatePassword(
        entryId: String,
        newPassword: String,
        onResult: (PasswordOperationResult) -> Unit = {}
    ) = viewModelScope.launch {
        val entry = repository.getById(entryId) ?: run {
            onResult(PasswordOperationResult.Error("Запись не найдена"))
            return@launch
        }

        val validation = PasswordValidator.validateNewPasswordForEntry(entry, newPassword, appContext)
        if (!validation.isValid) {
            onResult(PasswordOperationResult.Error(validation.errorMessage ?: "Ошибка валидации"))
            return@launch
        }

        val now = System.currentTimeMillis()
        val newNextRotationDate = if (entry.rotationEnabled) {
            now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
        } else null

        val newFingerprint = PasswordValidator.buildPasswordFingerprint(newPassword, appContext)
        val oldFingerprint = PasswordValidator.buildPasswordFingerprint(entry.password, appContext)

        val updated = entry.addToPasswordHistory(
            oldPassword = entry.password,
            generationType = entry.generationType,
            oldPasswordFingerprint = oldFingerprint
        ).copy(
            encryptedPassword = CryptoUtils.encrypt(newPassword),
            passwordFingerprint = newFingerprint,
            lastChanged = now,
            nextRotationDate = newNextRotationDate
        )
        repository.update(updated)
        onResult(PasswordOperationResult.Success)
    }

    fun updateRotationSettings(entryId: String, enabled: Boolean, periodMonths: Int) = viewModelScope.launch {
        val entry = repository.getById(entryId) ?: return@launch

        val now = System.currentTimeMillis()
        val newNextRotationDate = if (enabled) {
            now + (periodMonths * 30L * 24 * 60 * 60 * 1000)
        } else null

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
        newMnemonicOptionsJson: String? = null,
        onResult: (PasswordOperationResult) -> Unit = {}
    ) = viewModelScope.launch {
        val entry = repository.getById(entryId) ?: run {
            onResult(PasswordOperationResult.Error("Запись не найдена"))
            return@launch
        }

        val validation = PasswordValidator.validateNewPasswordForEntry(entry, newPassword, appContext)
        if (!validation.isValid) {
            onResult(PasswordOperationResult.Error(validation.errorMessage ?: "Ошибка валидации"))
            return@launch
        }

        val now = System.currentTimeMillis()
        val newNextRotationDate = if (entry.rotationEnabled) {
            now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
        } else null

        val newFingerprint = PasswordValidator.buildPasswordFingerprint(newPassword, appContext)
        val oldFingerprint = PasswordValidator.buildPasswordFingerprint(entry.password, appContext)

        val updated = entry.addToPasswordHistory(
            oldPassword = entry.password,
            generationType = entry.generationType,
            oldPasswordFingerprint = oldFingerprint
        ).copy(
            encryptedPassword = CryptoUtils.encrypt(newPassword),
            textHint = newHint,
            generationType = newGenerationType,
            passwordFingerprint = newFingerprint,
            mnemonicPhraseHint = newMnemonicPhraseHint,
            mnemonicOptionsJson = newMnemonicOptionsJson,
            lastChanged = now,
            nextRotationDate = newNextRotationDate
        )
        repository.update(updated)
        onResult(PasswordOperationResult.Success)
    }

    fun bulkReplacePasswords(
        replacements: List<PasswordReplacement>,
        onResult: (PasswordOperationResult) -> Unit = {}
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val errors = mutableListOf<String>()

        for (replacement in replacements) {
            val entry = repository.getById(replacement.entryId) ?: continue

            val validation = PasswordValidator.validateNewPasswordForEntry(entry, replacement.newPassword, appContext)
            if (!validation.isValid) {
                errors.add("${entry.service}: ${validation.errorMessage}")
                continue
            }

            val newNextRotationDate = if (entry.rotationEnabled) {
                now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
            } else null

            val newFingerprint = PasswordValidator.buildPasswordFingerprint(replacement.newPassword, appContext)
            val oldFingerprint = PasswordValidator.buildPasswordFingerprint(entry.password, appContext)

            val updated = entry.addToPasswordHistory(
                oldPassword = entry.password,
                generationType = entry.generationType,
                oldPasswordFingerprint = oldFingerprint
            ).copy(
                encryptedPassword = CryptoUtils.encrypt(replacement.newPassword),
                textHint = replacement.newHint,
                generationType = replacement.newGenerationType,
                passwordFingerprint = newFingerprint,
                mnemonicPhraseHint = replacement.newMnemonicPhraseHint,
                mnemonicOptionsJson = replacement.newMnemonicOptionsJson,
                lastChanged = now,
                nextRotationDate = newNextRotationDate
            )
            repository.update(updated)
        }

        if (errors.isEmpty()) {
            onResult(PasswordOperationResult.Success)
        } else {
            onResult(PasswordOperationResult.Error(errors.joinToString("\n")))
        }
    }

    fun buildPasswordShufflePlan(
        entryIds: List<String>,
        onResult: (PasswordShuffleResult) -> Unit
    ) = viewModelScope.launch {
        if (entryIds.size < 2) {
            onResult(PasswordShuffleResult(false, errorMessage = "Для перемешивания нужно минимум 2 сервиса"))
            return@launch
        }

        val entries = entryIds.mapNotNull { repository.getById(it) }
        if (entries.size != entryIds.size) {
            onResult(PasswordShuffleResult(false, errorMessage = "Не все записи найдены"))
            return@launch
        }

        val profileIds = entries.map { it.profileId }.toSet()
        if (profileIds.size > 1) {
            onResult(PasswordShuffleResult(false, errorMessage = "Все записи должны быть из одного профиля"))
            return@launch
        }

        val assignments = tryBuildPermutation(entries)

        if (assignments == null) {
            onResult(PasswordShuffleResult(
                false,
                errorMessage = "Не удалось безопасно перемешать пароли. Выберите больше сервисов или используйте другой способ ротации."
            ))
        } else {
            onResult(PasswordShuffleResult(true, assignments = assignments))
        }
    }

    private suspend fun tryBuildPermutation(entries: List<Entry>): List<PasswordShuffleAssignment>? {
        val n = entries.size
        val sourceIndices = (0 until n).toMutableList()

        for (attempt in 0 until 100) {
            sourceIndices.shuffle()

            val assignments = mutableListOf<PasswordShuffleAssignment>()
            var valid = true
            var i = 0

            while (i < n && valid) {
                val targetEntry = entries[i]
                val sourceIndex = sourceIndices[i]
                val sourceEntry = entries[sourceIndex]

                if (targetEntry.id == sourceEntry.id) {
                    valid = false
                } else {
                    val sourcePassword = withContext(Dispatchers.Default) {
                        try { sourceEntry.password } catch (e: Exception) { null }
                    }

                    if (sourcePassword == null) {
                        valid = false
                    } else if (PasswordValidator.hasDuplicateCharacters(sourcePassword)) {
                        valid = false
                    } else if (PasswordValidator.wasPasswordUsedForEntry(targetEntry, sourcePassword, appContext)) {
                        valid = false
                    } else {
                        val lastHistory = targetEntry.getPasswordHistory().firstOrNull()
                        if (lastHistory?.encryptedOldPassword != null) {
                            try {
                                val oldPassword = CryptoUtils.decrypt(lastHistory.encryptedOldPassword)
                                if (!PasswordValidator.isAtLeast60PercentUnique(oldPassword, sourcePassword)) {
                                    valid = false
                                }
                            } catch (e: Exception) {
                                // ignore
                            }
                        }

                        if (valid) {
                            assignments.add(
                                PasswordShuffleAssignment(
                                    targetEntryId = targetEntry.id,
                                    sourceEntryId = sourceEntry.id,
                                    isValid = true
                                )
                            )
                        }
                    }
                }
                i++
            }

            if (valid && assignments.size == n) {
                return assignments
            }
        }

        return null
    }

    fun applyPasswordShuffle(
        assignments: List<PasswordShuffleAssignment>,
        onResult: (PasswordShuffleResult) -> Unit
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()

        val sourceEntries = assignments.mapNotNull { repository.getById(it.sourceEntryId) }
        val targetEntries = assignments.mapNotNull { repository.getById(it.targetEntryId) }

        if (sourceEntries.size != assignments.size || targetEntries.size != assignments.size) {
            onResult(PasswordShuffleResult(false, errorMessage = "Не все записи найдены"))
            return@launch
        }

        val sourcePasswords = mutableMapOf<String, String?>()
        for (entry in sourceEntries) {
            sourcePasswords[entry.id] = withContext(Dispatchers.Default) {
                try { entry.password } catch (e: Exception) { null }
            }
        }

        val errors = mutableListOf<String>()
        for (assignment in assignments) {
            val targetEntry = repository.getById(assignment.targetEntryId) ?: continue
            val sourcePassword = sourcePasswords[assignment.sourceEntryId] ?: continue

            val validation = PasswordValidator.validateNewPasswordForEntry(targetEntry, sourcePassword, appContext)
            if (!validation.isValid) {
                errors.add("${targetEntry.service}: ${validation.errorMessage}")
            }
        }

        if (errors.isNotEmpty()) {
            onResult(PasswordShuffleResult(false, errorMessage = errors.joinToString("\n")))
            return@launch
        }

        for (assignment in assignments) {
            val targetEntry = repository.getById(assignment.targetEntryId) ?: continue
            val sourcePassword = sourcePasswords[assignment.sourceEntryId] ?: continue
            val sourceEntry = sourceEntries.find { it.id == assignment.sourceEntryId } ?: continue

            val newFingerprint = PasswordValidator.buildPasswordFingerprint(sourcePassword, appContext)
            val oldFingerprint = PasswordValidator.buildPasswordFingerprint(targetEntry.password, appContext)
            val newNextRotationDate = if (targetEntry.rotationEnabled) {
                now + (targetEntry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
            } else null

            val updated = targetEntry.addToPasswordHistory(
                oldPassword = targetEntry.password,
                generationType = "shuffled",
                oldPasswordFingerprint = oldFingerprint,
                relatedService = sourceEntry.service,
                relatedEntryId = sourceEntry.id
            ).copy(
                encryptedPassword = CryptoUtils.encrypt(sourcePassword),
                passwordFingerprint = newFingerprint,
                generationType = "shuffled",
                lastChanged = now,
                nextRotationDate = newNextRotationDate
            )
            repository.update(updated)
        }

        onResult(PasswordShuffleResult(success = true, assignments = assignments))
    }

    suspend fun findEntriesUsingPasswordFingerprint(profileId: Int, fingerprint: String): List<Entry> {
        return withContext(Dispatchers.IO) {
            repository.allEntries.firstOrNull()
                ?.filter { it.profileId == profileId }
                ?.filter { it.passwordFingerprint == fingerprint }
                ?: emptyList()
        }
    }
}
