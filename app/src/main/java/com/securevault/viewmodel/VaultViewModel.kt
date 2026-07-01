package com.securevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.VaultRepository
import com.securevault.utils.CryptoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PasswordReplacement(
    val entryId: String,
    val newPassword: String,
    val newHint: String?,
    val generationType: String
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

    // ✅ ДОБАВЛЕНО: метод для ReminderScreen
    fun updatePassword(entryId: String, newPassword: String) = viewModelScope.launch {
        val entry = repository.getById(entryId) ?: return@launch
        
        val now = System.currentTimeMillis()
        val newNextRotationDate = if (entry.rotationEnabled) {
            now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
        } else {
            null
        }
        
        val updated = entry.addToPasswordHistory(entry.password, entry.generationType).copy(
            encryptedPassword = CryptoUtils.encrypt(newPassword),
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
        newGenerationType: String
    ) = viewModelScope.launch {
        val entry = repository.getById(entryId) ?: return@launch
        
        val now = System.currentTimeMillis()
        val newNextRotationDate = if (entry.rotationEnabled) {
            now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
        } else {
            null
        }
        
        val updated = entry.addToPasswordHistory(entry.password, entry.generationType).copy(
            encryptedPassword = CryptoUtils.encrypt(newPassword),
            textHint = newHint,
            generationType = newGenerationType,
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
            
            val updated = entry.addToPasswordHistory(entry.password, entry.generationType).copy(
                encryptedPassword = CryptoUtils.encrypt(replacement.newPassword),
                textHint = replacement.newHint,
                generationType = replacement.generationType,
                lastChanged = now,
                nextRotationDate = newNextRotationDate
            )
            repository.update(updated)
        }
    }
}
