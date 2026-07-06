package com.securevault.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.VaultRepository
import com.securevault.security.BruteForceGuard
import com.securevault.utils.CryptoUtils
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

data class PasswordShuffleAssignment(
    val targetEntryId: String,
    val sourceEntryId: String,
    val isValid: Boolean = true,
    val validationMessage: String? = null
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository,
    application: Application
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _rotationEntries = MutableStateFlow<List<Entry>>(emptyList())
    val rotationEntries: StateFlow<List<Entry>> = _rotationEntries.asStateFlow()

    private val _currentProfileId = MutableStateFlow<Int?>(null)
    val currentProfileId: StateFlow<Int?> = _currentProfileId.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    init {
        BruteForceGuard.init(appContext)
    }

    fun setCurrentProfile(profileId: Int?) {
        _currentProfileId.value = profileId
        if (profileId != null) {
            loadEntriesForProfile(profileId)
        } else {
            _entries.value = emptyList()
        }
    }

    private fun loadEntriesForProfile(profileId: Int) {
        viewModelScope.launch {
            _entries.value = repository.getByProfile(profileId)
            updateRotationEntries()
        }
    }

    private fun updateRotationEntries() {
        val profileId = _currentProfileId.value ?: return
        viewModelScope.launch {
            _rotationEntries.value = repository.getByProfile(profileId).filter { it.rotationEnabled }
        }
    }

    fun insert(entry: Entry) {
        viewModelScope.launch {
            repository.insert(entry)
            _currentProfileId.value?.let { loadEntriesForProfile(it) }
        }
    }

    fun delete(entry: Entry) {
        viewModelScope.launch {
            repository.delete(entry)
            _currentProfileId.value?.let { loadEntriesForProfile(it) }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                repository.deleteByProfile(profileId)
                loadEntriesForProfile(profileId)
            }
        }
    }

    fun toggleFavorite(entry: Entry) {
        viewModelScope.launch {
            val updated = entry.copy(isFavorite = !entry.isFavorite)
            repository.update(updated)
            _currentProfileId.value?.let { loadEntriesForProfile(it) }
        }
    }

    fun toggleFavoritesOnly() {
        _favoritesOnly.value = !_favoritesOnly.value
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
                val encryptedPwd = CryptoUtils.encrypt(newPassword)
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
                _currentProfileId.value?.let { loadEntriesForProfile(it) }
                onResult?.invoke(PasswordOperationResult.Success)
            } catch (e: Exception) {
                onResult?.invoke(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun bulkReplacePasswords(
        replacements: List<Triple<String, String, String>>,
        onResult: ((PasswordOperationResult) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                for ((entryId, newPassword, generationType) in replacements) {
                    val entry = repository.getById(entryId) ?: continue
                    val validation = PasswordValidator.validateNewPasswordForEntry(entry, newPassword, appContext)
                    if (!validation.isValid) continue

                    val now = System.currentTimeMillis()
                    val encryptedPwd = CryptoUtils.encrypt(newPassword)
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
                _currentProfileId.value?.let { loadEntriesForProfile(it) }
                onResult?.invoke(PasswordOperationResult.Success)
            } catch (e: Exception) {
                onResult?.invoke(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun buildPasswordShufflePlan(
        entryIds: List<String>,
        onResult: (ShufflePlanResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val entries = entryIds.mapNotNull { repository.getById(it) }
                if (entries.size < 2) {
                    onResult(ShufflePlanResult(false, "Нужно минимум 2 записи", emptyList()))
                    return@launch
                }

                val shuffled = entries.shuffled()
                val assignments = mutableListOf<PasswordShuffleAssignment>()

                for (i in shuffled.indices) {
                    val target = shuffled[i]
                    val source = shuffled[(i + 1) % shuffled.size]

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

                onResult(ShufflePlanResult(true, null, assignments))
            } catch (e: Exception) {
                onResult(ShufflePlanResult(false, "Ошибка: ${e.message}", emptyList()))
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
        onResult: ((PasswordOperationResult) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val passwordMap = mutableMapOf<String, String>()

                for (assignment in assignments) {
                    if (!assignment.isValid) continue
                    val sourceEntry = repository.getById(assignment.sourceEntryId) ?: continue
                    passwordMap[assignment.targetEntryId] = sourceEntry.password
                }

                for ((targetId, newPassword) in passwordMap) {
                    val targetEntry = repository.getById(targetId) ?: continue
                    val validation = PasswordValidator.validateNewPasswordForEntry(targetEntry, newPassword, appContext)
                    if (!validation.isValid) continue

                    val encryptedPwd = CryptoUtils.encrypt(newPassword)
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

                _currentProfileId.value?.let { loadEntriesForProfile(it) }
                onResult?.invoke(PasswordOperationResult.Success)
            } catch (e: Exception) {
                onResult?.invoke(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun findEntryById(entryId: String): Entry? {
        var result: Entry? = null
        kotlinx.coroutines.runBlocking {
            result = repository.getById(entryId)
        }
        return result
    }

    data class ShufflePlanResult(
        val success: Boolean,
        val errorMessage: String?,
        val assignments: List<PasswordShuffleAssignment>
    )
}
