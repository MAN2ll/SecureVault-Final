package com.securevault.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.BackupData
import com.securevault.data.Entry
import com.securevault.data.PasswordHistoryItem
import com.securevault.data.Profile
import com.securevault.data.VaultRepository
import com.securevault.security.ProfilePasswordHasher
import com.securevault.utils.BackupManager
import com.securevault.utils.CryptoUtils
import com.securevault.utils.ImportMode
import com.securevault.utils.ImportResult
import com.securevault.utils.JsonUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    application: Application,
    private val repository: VaultRepository
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _currentProfileId = MutableStateFlow<Int?>(null)
    val currentProfileId: StateFlow<Int?> = _currentProfileId.asStateFlow()

    // Блок 1.2: Состояние фильтра избранного
    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    val allEntries: StateFlow<List<Entry>> = repository.allEntries
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Учитывает фильтр избранного
    val entries: StateFlow<List<Entry>> = combine(
        allEntries, currentProfileId, favoritesOnly
    ) { entries, profileId, favOnly ->
        val filtered = if (profileId != null) {
            entries.filter { it.profileId == profileId }
        } else {
            entries
        }
        if (favOnly) filtered.filter { it.isFavorite } else filtered
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val rotationEntries: StateFlow<List<Entry>> = combine(
        allEntries, currentProfileId
    ) { entries, profileId ->
        val filtered = if (profileId != null) {
            entries.filter { it.profileId == profileId }
        } else {
            entries
        }
        filtered.filter { 
            it.rotationEnabled && 
            it.nextRotationDate != null && 
            it.nextRotationDate <= System.currentTimeMillis() 
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val profiles: StateFlow<List<Profile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val currentProfile: StateFlow<Profile?> = combine(
        profiles, currentProfileId
    ) { profileList, id ->
        profileList.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    init {
        viewModelScope.launch {
            profiles.collect { list ->
                if (_currentProfileId.value == null && list.isNotEmpty()) {
                    _currentProfileId.value = list.first().id
                }
            }
        }
    }

    // Блок 1.1: Принимает Int? для выхода из профиля
    fun setCurrentProfile(profileId: Int?) {
        _currentProfileId.value = profileId
    }

    // Блок 1.3: Переключение фильтра избранного
    fun toggleFavoritesOnly() {
        _favoritesOnly.value = !_favoritesOnly.value
    }

    // Блок 1.3: Переключение избранного для записи
    fun toggleFavorite(entry: Entry, onResult: (PasswordOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val updated = entry.copy(isFavorite = !entry.isFavorite)
                repository.update(updated)
                onResult(PasswordOperationResult.Success())
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun findEntryById(entryId: String): Entry? {
        return allEntries.value.find { it.id == entryId }
    }

    fun insert(entry: Entry, onResult: (PasswordOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.insert(entry)
                onResult(PasswordOperationResult.Success("Запись добавлена"))
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun insertEntry(entry: Entry, onResult: (PasswordOperationResult) -> Unit) {
        insert(entry, onResult)
    }

    fun updateEntry(entry: Entry, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try {
                repository.update(entry)
                onResult(PasswordOperationResult.Success("Запись обновлена"))
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    // Блок 1.4: Удаление с проверкой профиля
    fun deleteEntry(
        entryId: String,
        expectedProfileId: Int,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val entry = allEntries.value.find { it.id == entryId }
                if (entry == null) {
                    onResult(PasswordOperationResult.Error("Запись не найдена"))
                    return@launch
                }
                if (entry.profileId != expectedProfileId) {
                    onResult(PasswordOperationResult.Error("Запись не принадлежит текущему профилю"))
                    return@launch
                }
                repository.delete(entry)
                onResult(PasswordOperationResult.Success("Запись удалена"))
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    // Блок 1.5: Массовое удаление
    fun deleteEntries(
        entryIds: List<String>,
        expectedProfileId: Int,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                var deletedCount = 0
                for (id in entryIds) {
                    val entry = allEntries.value.find { it.id == id }
                    if (entry != null && entry.profileId == expectedProfileId) {
                        repository.delete(entry)
                        deletedCount++
                    }
                }
                onResult(PasswordOperationResult.Success("Удалено записей: $deletedCount"))
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    //  Блок 1.6: Удаление всех записей текущего профиля
    fun deleteAllEntriesInProfile(
        profileId: Int,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteEntriesByProfileId(profileId)
                onResult(PasswordOperationResult.Success("Все записи профиля удалены"))
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    // Резервный метод удаления (если вызывается без expectedProfileId)
    fun deleteEntry(entryId: String, onResult: (PasswordOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val entry = allEntries.value.find { it.id == entryId }
                if (entry != null) {
                    repository.delete(entry)
                }
                onResult(PasswordOperationResult.Success("Запись удалена"))
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun addToPasswordHistory(
        entry: Entry,
        oldEncryptedPassword: String?,
        type: String,
        relatedService: String? = null,
        onResult: (PasswordOperationResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val history = entry.getPasswordHistory().toMutableList()
                history.add(
                    PasswordHistoryItem(
                        date = System.currentTimeMillis(),
                        encryptedOldPassword = oldEncryptedPassword,
                        type = type,
                        relatedService = relatedService
                    )
                )
                val updated = entry.copy(
                    passwordHistoryJson = JsonUtils.toJson(history),
                    lastChanged = System.currentTimeMillis()
                )
                repository.update(updated)
                onResult(PasswordOperationResult.Success())
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    // === Перегрузки replacePassword (сохраняем совместимость) ===

    fun replacePassword(
        entry: Entry,
        newPassword: String,
        generationType: String,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        replacePasswordInternal(entry.id, newPassword, generationType, null, generationType, null, null, onResult)
    }

    fun replacePassword(
        entryId: String,
        newPassword: String,
        generationType: String,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        replacePasswordInternal(entryId, newPassword, generationType, null, generationType, null, null, onResult)
    }

    fun replacePassword(
        entry: Entry,
        newPassword: String,
        newHint: String?,
        newGenerationType: String,
        newMnemonicPhraseHint: String?,
        newMnemonicOptionsJson: String?,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        replacePasswordInternal(entry.id, newPassword, newGenerationType, newHint, newGenerationType, newMnemonicPhraseHint, newMnemonicOptionsJson, onResult)
    }

    fun replacePassword(
        entryId: String,
        newPassword: String,
        newHint: String?,
        newGenerationType: String,
        newMnemonicPhraseHint: String?,
        newMnemonicOptionsJson: String?,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        replacePasswordInternal(entryId, newPassword, newGenerationType, newHint, newGenerationType, newMnemonicPhraseHint, newMnemonicOptionsJson, onResult)
    }

    fun replacePassword(
        entryId: String,
        newHint: String?,
        newGenerationType: String,
        newMnemonicPhraseHint: String?,
        newMnemonicOptionsJson: String?,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val entry = allEntries.value.find { it.id == entryId } ?: return@launch
                val updated = entry.copy(
                    textHint = newHint,
                    generationType = newGenerationType,
                    mnemonicPhraseHint = newMnemonicPhraseHint,
                    mnemonicOptionsJson = newMnemonicOptionsJson,
                    lastChanged = System.currentTimeMillis()
                )
                repository.update(updated)
                onResult(PasswordOperationResult.Success("Параметры обновлены"))
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    private fun replacePasswordInternal(
        entryId: String,
        newPassword: String,
        actualGenerationType: String,
        newHint: String?,
        newGenerationType: String,
        newMnemonicPhraseHint: String?,
        newMnemonicOptionsJson: String?,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val entry = allEntries.value.find { it.id == entryId } ?: return@launch
                val newEncrypted = withContext(Dispatchers.Default) {
                    CryptoUtils.encrypt(newPassword)
                }
                
                val history = entry.getPasswordHistory().toMutableList()
                history.add(
                    PasswordHistoryItem(
                        date = System.currentTimeMillis(),
                        encryptedOldPassword = entry.encryptedPassword,
                        type = actualGenerationType,
                        relatedService = null
                    )
                )
                
                val updated = entry.copy(
                    encryptedPassword = newEncrypted,
                    passwordHistoryJson = JsonUtils.toJson(history),
                    lastChanged = System.currentTimeMillis(),
                    nextRotationDate = if (entry.rotationEnabled) {
                        System.currentTimeMillis() + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
                    } else null,
                    textHint = newHint,
                    generationType = newGenerationType,
                    mnemonicPhraseHint = newMnemonicPhraseHint,
                    mnemonicOptionsJson = newMnemonicOptionsJson
                )
                repository.update(updated)
                onResult(PasswordOperationResult.Success("Пароль заменён"))
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun applyManagedShuffle(
        assignments: Map<String, String?>,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val currentEntries = allEntries.value
                var successCount = 0
                var errorCount = 0
                
                for ((targetEntryId, sourceEntryId) in assignments) {
                    val targetEntry = currentEntries.find { it.id == targetEntryId }
                    if (targetEntry == null) {
                        errorCount++
                        continue
                    }
                    
                    if (sourceEntryId == null) continue
                    
                    val sourceEntry = currentEntries.find { it.id == sourceEntryId }
                    if (sourceEntry == null) {
                        errorCount++
                        continue
                    }
                    
                    val newEncrypted = withContext(Dispatchers.Default) {
                        CryptoUtils.encrypt(CryptoUtils.decrypt(sourceEntry.encryptedPassword))
                    }
                    val oldEncrypted = targetEntry.encryptedPassword
                    
                    val history = targetEntry.getPasswordHistory().toMutableList()
                    history.add(
                        PasswordHistoryItem(
                            date = System.currentTimeMillis(),
                            encryptedOldPassword = oldEncrypted,
                            type = "shuffle",
                            relatedService = sourceEntry.service
                        )
                    )
                    
                    val updated = targetEntry.copy(
                        encryptedPassword = newEncrypted,
                        passwordHistoryJson = JsonUtils.toJson(history),
                        lastChanged = System.currentTimeMillis(),
                        nextRotationDate = if (targetEntry.rotationEnabled) {
                            System.currentTimeMillis() + (targetEntry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
                        } else null,
                        generationType = sourceEntry.generationType,
                        mnemonicPhraseHint = sourceEntry.mnemonicPhraseHint,
                        mnemonicOptionsJson = sourceEntry.mnemonicOptionsJson
                    )
                    
                    repository.update(updated)
                    successCount++
                }
                
                if (errorCount == 0) {
                    onResult(PasswordOperationResult.Success("Перекрёстная ротация выполнена: $successCount записей"))
                } else {
                    onResult(PasswordOperationResult.Error("Выполнено: $successCount, ошибок: $errorCount"))
                }
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun bulkReplacePasswords(
        replacements: List<BulkPasswordReplacement>,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val currentEntries = allEntries.value
                for (replacement in replacements) {
                    val entry = currentEntries.find { it.id == replacement.entryId } ?: continue
                    val oldEncrypted = entry.encryptedPassword
                    
                    val newEncrypted = withContext(Dispatchers.Default) {
                        CryptoUtils.encrypt(replacement.newPassword)
                    }
                    
                    val history = entry.getPasswordHistory().toMutableList()
                    history.add(
                        PasswordHistoryItem(
                            date = System.currentTimeMillis(),
                            encryptedOldPassword = oldEncrypted,
                            type = replacement.generationType,
                            relatedService = null
                        )
                    )
                    
                    val updated = entry.copy(
                        encryptedPassword = newEncrypted,
                        passwordHistoryJson = JsonUtils.toJson(history),
                        lastChanged = System.currentTimeMillis(),
                        textHint = replacement.textHint ?: entry.textHint,
                        mnemonicPhraseHint = replacement.mnemonicPhraseHint ?: entry.mnemonicPhraseHint,
                        mnemonicOptionsJson = replacement.mnemonicOptionsJson ?: entry.mnemonicOptionsJson
                    )
                    repository.update(updated)
                }
                onResult(PasswordOperationResult.Success("Пароли заменены"))
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun addProfile(profile: Profile, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try {
                repository.insertProfile(profile)
                onResult(PasswordOperationResult.Success("Профиль добавлен"))
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun updateProfile(profile: Profile, onResult: (PasswordOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updateProfile(profile)
                onResult(PasswordOperationResult.Success())
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun deleteProfile(profileId: Int, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteProfile(profileId)
                onResult(PasswordOperationResult.Success("Профиль удалён"))
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun setProfilePin(
        profileId: Int,
        pin: String,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val profile = profiles.value.find { it.id == profileId } ?: return@launch
                val salt = ProfilePasswordHasher.generateSalt()
                val hash = ProfilePasswordHasher.hash(pin, salt)
                val updated = profile.copy(
                    passwordHash = hash,
                    passwordSalt = salt
                )
                repository.updateProfile(updated)
                onResult(PasswordOperationResult.Success("PIN установлен"))
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun verifyProfilePin(profileId: Int, pin: String): Boolean {
        val profile = profiles.value.find { it.id == profileId } ?: return false
        val hash = profile.passwordHash ?: return false
        val salt = profile.passwordSalt ?: return false
        return ProfilePasswordHasher.verify(pin, hash, salt)
    }

    suspend fun exportAllProfiles(): BackupData {
        return BackupManager.exportAllProfiles(repository, appContext)
    }

    suspend fun importBackup(
        backupData: BackupData,
        mode: ImportMode,
        newPin: String?
    ): ImportResult {
        return BackupManager.importBackup(repository, backupData, mode, newPin, appContext)
    }
}

data class BulkPasswordReplacement(
    val entryId: String,
    val newPassword: String,
    val generationType: String,
    val textHint: String? = null,
    val mnemonicPhraseHint: String? = null,
    val mnemonicOptionsJson: String? = null
)

data class PasswordShuffleAssignment(
    val entryId: String,
    val newPassword: String,
    val generationType: String,
    val targetProfileId: Int? = null
)
