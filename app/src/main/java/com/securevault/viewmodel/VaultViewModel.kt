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

    // Изменено: учитывает favoritesOnly
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

    //  Блок 1.1: Принимает Int? для выхода из профиля
    fun setCurrentProfile(profileId: Int?) {
        _currentProfileId.value = profileId
    }

    // Блок 1.3: Переключение фильтра избранного
    fun toggleFavoritesOnly() {
        _favoritesOnly.value = !_favoritesOnly.value
    }

    //  Блок 1.3: Переключение избранного для записи
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
                for (entryId in entryIds) {
                    val entry = allEntries.value.find { it.id == entryId }
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

    // Блок 1.6: Удаление всех записей профиля
    fun deleteAllEntriesInProfile(
        profileId: Int,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Если в репозитории есть метод deleteEntriesByProfileId, используем его
                // Иначе удаляем по одной
                val entriesToDelete = allEntries.value.filter { it.profileId == profileId }
                for (entry in entriesToDelete) {
                    repository.delete(entry)
                }
                onResult(PasswordOperationResult.Success("Удалено записей: ${entriesToDelete.size}"))
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

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
