package com.securevault.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.VaultRepository
import com.securevault.utils.PasswordValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    //  Используем repository.allEntries (свойство, а не метод)
    val entries: StateFlow<List<Entry>> = repository.allEntries
    
    //  Используем getEntriesWithRotation()
    private val _rotationEntries = MutableStateFlow<List<Entry>>(emptyList())
    val rotationEntries: StateFlow<List<Entry>> = _rotationEntries.asStateFlow()
    
    val favoritesOnly = MutableStateFlow(false)

    private val _currentProfileId = MutableStateFlow<Int?>(null)
    val currentProfileId: StateFlow<Int?> = _currentProfileId.asStateFlow()

    init {
        loadRotationEntries()
    }

    private fun loadRotationEntries() {
        viewModelScope.launch {
            _rotationEntries.value = repository.getEntriesWithRotation()
        }
    }

    fun setCurrentProfile(profileId: Int?) {
        _currentProfileId.value = profileId
    }

    fun toggleFavoritesOnly() {
        favoritesOnly.value = !favoritesOnly.value
    }

    fun toggleFavorite(entry: Entry) {
        viewModelScope.launch {
            repository.update(entry.copy(isFavorite = !entry.isFavorite))
        }
    }

    // Для совместимости с другими файлами
    fun insert(entry: Entry) {
        viewModelScope.launch {
            repository.insert(entry)
        }
    }

    //  Сохранение с callback
    fun insertEntry(
        entry: Entry,
        onResult: (PasswordOperationResult) -> Unit
    ) = viewModelScope.launch {
        try {
            repository.insert(entry)
            onResult(PasswordOperationResult.Success)
        } catch (e: Exception) {
            onResult(PasswordOperationResult.Error(e.message ?: "Ошибка сохранения"))
        }
    }

    //  Обновление с callback
    fun updateEntry(
        entry: Entry,
        onResult: (PasswordOperationResult) -> Unit
    ) = viewModelScope.launch {
        try {
            repository.update(entry)
            onResult(PasswordOperationResult.Success)
        } catch (e: Exception) {
            onResult(PasswordOperationResult.Error(e.message ?: "Ошибка обновления"))
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun findEntryById(entryId: String): Entry? {
        return repository.getByIdBlocking(entryId)
    }

    fun replacePassword(
        entryId: String,
        newPassword: String,
        newHint: String?,
        newGenerationType: String,
        newMnemonicPhraseHint: String?,
        newMnemonicOptionsJson: String?,
        onResult: ((PasswordOperationResult) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val entry = repository.getById(entryId)
                if (entry == null) {
                    onResult?.invoke(PasswordOperationResult.Error("Запись не найдена"))
                    return@launch
                }

                val validation = PasswordValidator.validateNewPasswordForEntry(entry, newPassword, appContext)
                if (!validation.isValid) {
                    onResult?.invoke(PasswordOperationResult.Error(validation.errorMessage ?: "Ошибка валидации"))
                    return@launch
                }

                val now = System.currentTimeMillis()
                val encryptedPwd = com.securevault.utils.CryptoUtils.encrypt(newPassword)
                val newFingerprint = PasswordValidator.buildPasswordFingerprint(newPassword, appContext)
                val oldFingerprint = PasswordValidator.buildPasswordFingerprint(entry.password, appContext)

                val updatedEntry = entry.addToPasswordHistory(
                    oldPassword = entry.password,
                    generationType = entry.generationType,
                    oldPasswordFingerprint = oldFingerprint
                ).copy(
                    encryptedPassword = encryptedPwd,
                    passwordFingerprint = newFingerprint,
                    lastChanged = now,
                    generationType = newGenerationType,
                    textHint = newHint ?: entry.textHint,
                    mnemonicPhraseHint = newMnemonicPhraseHint ?: entry.mnemonicPhraseHint,
                    mnemonicOptionsJson = newMnemonicOptionsJson ?: entry.mnemonicOptionsJson,
                    nextRotationDate = if (entry.rotationEnabled) {
                        now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
                    } else {
                        null
                    }
                )

                repository.update(updatedEntry)
                onResult?.invoke(PasswordOperationResult.Success)
            } catch (e: Exception) {
                onResult?.invoke(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    // Принимает List<Triple<String, String, String>>
    fun bulkReplacePasswords(
        replacements: List<Triple<String, String, String>>, // entryId, newPassword, generationType
        onResult: ((PasswordOperationResult) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                for ((entryId, newPassword, generationType) in replacements) {
                    val entry = repository.getById(entryId)
                    if (entry != null) {
                        val validation = PasswordValidator.validateNewPasswordForEntry(entry, newPassword, appContext)
                        if (validation.isValid) {
                            val now = System.currentTimeMillis()
                            val encryptedPwd = com.securevault.utils.CryptoUtils.encrypt(newPassword)
                            val newFingerprint = PasswordValidator.buildPasswordFingerprint(newPassword, appContext)
                            val oldFingerprint = PasswordValidator.buildPasswordFingerprint(entry.password, appContext)

                            val updatedEntry = entry.addToPasswordHistory(
                                oldPassword = entry.password,
                                generationType = entry.generationType,
                                oldPasswordFingerprint = oldFingerprint
                            ).copy(
                                encryptedPassword = encryptedPwd,
                                passwordFingerprint = newFingerprint,
                                lastChanged = now,
                                generationType = generationType,
                                nextRotationDate = if (entry.rotationEnabled) {
                                    now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
                                } else {
                                    null
                                }
                            )
                            repository.update(updatedEntry)
                        }
                    }
                }
                onResult?.invoke(PasswordOperationResult.Success)
            } catch (e: Exception) {
                onResult?.invoke(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun buildPasswordShufflePlan(
        selectedEntryIds: List<String>,
        onResult: (PasswordShufflePlanResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (selectedEntryIds.size < 2) {
                    onResult(PasswordShufflePlanResult(false, emptyList(), "Нужно минимум 2 записи"))
                    return@launch
                }

                val entries = selectedEntryIds.mapNotNull { repository.getById(it) }
                if (entries.size != selectedEntryIds.size) {
                    onResult(PasswordShufflePlanResult(false, emptyList(), "Не все записи найдены"))
                    return@launch
                }

                val profileId = entries.first().profileId
                if (entries.any { it.profileId != profileId }) {
                    onResult(PasswordShufflePlanResult(false, emptyList(), "Все записи должны быть из одного профиля"))
                    return@launch
                }

                val assignments = mutableListOf<PasswordShuffleAssignment>()
                val usedSources = mutableSetOf<String>()

                for (target in entries) {
                    val availableSources = entries.filter { source ->
                        source.id != target.id && source.id !in usedSources
                    }

                    if (availableSources.isEmpty()) {
                        onResult(PasswordShufflePlanResult(false, emptyList(), "Не удалось построить схему"))
                        return@launch
                    }

                    val source = availableSources.first()
                    usedSources.add(source.id)

                    val validation = validatePasswordShuffleAssignment(target.id, source.id, assignments)
                    assignments.add(
                        PasswordShuffleAssignment(
                            targetEntryId = target.id,
                            sourceEntryId = source.id,
                            isValid = validation.isValid,
                            validationMessage = validation.errorMessage
                        )
                    )
                }

                onResult(PasswordShufflePlanResult(true, assignments, null))
            } catch (e: Exception) {
                onResult(PasswordShufflePlanResult(false, emptyList(), "Ошибка: ${e.message}"))
            }
        }
    }

    fun validatePasswordShuffleAssignment(
        targetEntryId: String,
        sourceEntryId: String,
        currentAssignments: List<PasswordShuffleAssignment>
    ): PasswordValidator.ValidationResult {
        if (targetEntryId == sourceEntryId) {
            return PasswordValidator.ValidationResult(false, "Сервис не может получить свой же пароль")
        }

        val usedCount = currentAssignments.count {
            it.sourceEntryId == sourceEntryId && it.targetEntryId != targetEntryId
        }
        if (usedCount > 0) {
            return PasswordValidator.ValidationResult(false, "Этот пароль уже назначен другому сервису")
        }

        return PasswordValidator.ValidationResult(true)
    }

    fun applyPasswordShuffle(
        assignments: List<PasswordShuffleAssignment>,
        onResult: (PasswordShufflePlanResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                for (assignment in assignments) {
                    if (!assignment.isValid) {
                        onResult(PasswordShufflePlanResult(false, emptyList(), "Есть невалидные назначения"))
                        return@launch
                    }

                    val sourceEntry = repository.getById(assignment.sourceEntryId)
                    val targetEntry = repository.getById(assignment.targetEntryId)

                    if (sourceEntry == null || targetEntry == null) {
                        onResult(PasswordShufflePlanResult(false, emptyList(), "Запись не найдена"))
                        return@launch
                    }

                    val newPassword = sourceEntry.password
                    val validation = PasswordValidator.validateNewPasswordForEntry(targetEntry, newPassword, appContext)
                    if (!validation.isValid) {
                        onResult(PasswordShufflePlanResult(false, emptyList(), validation.errorMessage ?: "Ошибка валидации"))
                        return@launch
                    }

                    val now = System.currentTimeMillis()
                    val encryptedPwd = com.securevault.utils.CryptoUtils.encrypt(newPassword)
                    val newFingerprint = PasswordValidator.buildPasswordFingerprint(newPassword, appContext)
                    val oldFingerprint = PasswordValidator.buildPasswordFingerprint(targetEntry.password, appContext)

                    val updatedEntry = targetEntry.addToPasswordHistory(
                        oldPassword = targetEntry.password,
                        generationType = targetEntry.generationType,
                        oldPasswordFingerprint = oldFingerprint
                    ).copy(
                        encryptedPassword = encryptedPwd,
                        passwordFingerprint = newFingerprint,
                        lastChanged = now,
                        generationType = "shuffled",
                        nextRotationDate = if (targetEntry.rotationEnabled) {
                            now + (targetEntry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
                        } else {
                            null
                        }
                    )

                    repository.update(updatedEntry)
                }

                onResult(PasswordShufflePlanResult(true, emptyList(), null))
            } catch (e: Exception) {
                onResult(PasswordShufflePlanResult(false, emptyList(), "Ошибка: ${e.message}"))
            }
        }
    }
}

data class PasswordShuffleAssignment(
    val targetEntryId: String,
    val sourceEntryId: String,
    val isValid: Boolean,
    val validationMessage: String?
)

data class PasswordShufflePlanResult(
    val success: Boolean,
    val assignments: List<PasswordShuffleAssignment>,
    val errorMessage: String?
)
