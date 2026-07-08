package com.securevault.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.VaultRepository
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    //  ОБЪЯВЛЕНО ПЕРЕД init И entries
    private val _currentProfileId = MutableStateFlow<Int?>(null)
    val currentProfileId: StateFlow<Int?> = _currentProfileId.asStateFlow()

    //  Фильтрация записей по текущему профилю
    val entries: StateFlow<List<Entry>> = repository.allEntries
        .combine(_currentProfileId) { allEntries, profileId ->
            if (profileId == null) emptyList()
            else allEntries.filter { it.profileId == profileId }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _rotationEntries = MutableStateFlow<List<Entry>>(emptyList())
    val rotationEntries: StateFlow<List<Entry>> = _rotationEntries.asStateFlow()

    val favoritesOnly = MutableStateFlow(false)

    init {
        // Перезагружаем rotationEntries при смене профиля
        viewModelScope.launch {
            _currentProfileId.collect { profileId ->
                if (profileId != null) {
                    loadRotationEntries(profileId)
                } else {
                    _rotationEntries.value = emptyList()
                }
            }
        }
    }

    private fun loadRotationEntries(profileId: Int) {
        viewModelScope.launch {
            val allRotation = repository.getEntriesWithRotation()
            _rotationEntries.value = allRotation.filter { it.profileId == profileId }
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

    fun insert(entry: Entry) {
        viewModelScope.launch {
            repository.insert(entry)
        }
    }

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

    fun deleteEntry(
        entryId: String,
        expectedProfileId: Int,
        onResult: (PasswordOperationResult) -> Unit
    ) = viewModelScope.launch {
        try {
            val entry = repository.getById(entryId)
            if (entry == null) {
                onResult(PasswordOperationResult.Error("Запись не найдена"))
                return@launch
            }
            if (entry.profileId != expectedProfileId) {
                onResult(PasswordOperationResult.Error("Запись принадлежит другому профилю"))
                return@launch
            }
            repository.delete(entry)
            onResult(PasswordOperationResult.Success)
        } catch (e: Exception) {
            onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
        }
    }

    fun deleteEntries(
        entryIds: List<String>,
        expectedProfileId: Int,
        onResult: (PasswordOperationResult) -> Unit
    ) = viewModelScope.launch {
        try {
            for (entryId in entryIds) {
                val entry = repository.getById(entryId)
                if (entry != null && entry.profileId == expectedProfileId) {
                    repository.delete(entry)
                }
            }
            onResult(PasswordOperationResult.Success)
        } catch (e: Exception) {
            onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
        }
    }

    fun deleteAllEntriesInProfile(
        profileId: Int,
        onResult: (PasswordOperationResult) -> Unit
    ) = viewModelScope.launch {
        try {
            repository.deleteEntriesByProfileId(profileId)
            onResult(PasswordOperationResult.Success)
        } catch (e: Exception) {
            onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
        }
    }

    fun deleteAll() {
        val profileId = _currentProfileId.value ?: return
        viewModelScope.launch {
            repository.deleteEntriesByProfileId(profileId)
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
                    val entry = repository.getById(entryId)
                    if (entry != null) {
                        val validation = PasswordValidator.validateNewPasswordForEntry(entry, newPassword, appContext)
                        if (validation.isValid) {
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
                    }
                }
                onResult?.invoke(PasswordOperationResult.Success)
            } catch (e: Exception) {
                onResult?.invoke(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    //  Циклическая перестановка паролей с предварительной проверкой
    fun shufflePasswords(
        entries: List<Entry>,
        onResult: (PasswordOperationResult) -> Unit
    ) = viewModelScope.launch {
        try {
            if (entries.size < 2) {
                onResult(PasswordOperationResult.Error("Нужно минимум 2 записи для перестановки"))
                return@launch
            }

            // 1. Расшифровываем все пароли заранее
            val decryptedPasswords = entries.map { entry ->
                try {
                    entry.password
                } catch (e: Exception) {
                    throw Exception("Не удалось расшифровать пароль для '${entry.service}': ${e.message}")
                }
            }

            val now = System.currentTimeMillis()
            val updates = mutableListOf<Triple<Entry, String, String>>()

            // 2. ПРЕДВАРИТЕЛЬНАЯ ПРОВЕРКА
            for (i in entries.indices) {
                val targetEntry = entries[i]
                val sourceIndex = (i + 1) % entries.size
                val newPassword = decryptedPasswords[sourceIndex]

                if (newPassword == targetEntry.password) continue

                val validation = PasswordValidator.validateNewPasswordForEntry(
                    targetEntry, newPassword, appContext
                )

                if (!validation.isValid) {
                    onResult(PasswordOperationResult.Error(
                        "Невозможно выполнить ротацию: '${targetEntry.service}' -> ${validation.errorMessage}"
                    ))
                    return@launch
                }

                val encryptedPwd = CryptoUtils.encrypt(newPassword)
                val newFingerprint = PasswordValidator.buildPasswordFingerprint(newPassword, appContext)
                updates.add(Triple(targetEntry, encryptedPwd, newFingerprint))
            }

            // 3. Применяем изменения
            val oldFingerprints = entries.map {
                PasswordValidator.buildPasswordFingerprint(it.password, appContext)
            }

            for (i in entries.indices) {
                val targetEntry = entries[i]
                val updateIndex = updates.indexOfFirst { it.first.id == targetEntry.id }
                if (updateIndex < 0) continue

                val (_, encryptedPwd, newFingerprint) = updates[updateIndex]
                val oldFingerprint = oldFingerprints[i]

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

            onResult(PasswordOperationResult.Success)
        } catch (e: Exception) {
            onResult(PasswordOperationResult.Error("Ошибка перестановки: ${e.message}"))
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
