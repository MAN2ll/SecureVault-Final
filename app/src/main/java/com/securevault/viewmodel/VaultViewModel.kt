package com.securevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.VaultRepository
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    fun setCurrentProfile(profileId: Int?) { _currentProfileId.value = profileId }
    fun toggleFavoritesOnly() { _favoritesOnly.value = !_favoritesOnly.value }

    fun insert(entry: Entry) = viewModelScope.launch { repository.insert(entry) }
    fun update(entry: Entry) = viewModelScope.launch { repository.update(entry) }
    fun delete(entry: Entry) = viewModelScope.launch { repository.delete(entry) }
    fun deleteAll() = viewModelScope.launch { repository.deleteAll() }

    fun toggleFavorite(entry: Entry) = viewModelScope.launch {
        repository.update(entry.copy(isFavorite = !entry.isFavorite))
    }

    fun updatePassword(id: String, newPassword: String) = viewModelScope.launch {
        val entry = repository.getById(id) ?: return@launch
        val updated = entry.addToPasswordHistory(entry.password).copy(
            encryptedPassword = CryptoUtils.encrypt(newPassword),
            lastChanged = System.currentTimeMillis()
        )
        repository.update(updated)
    }

    fun rotatePassword(id: String) = viewModelScope.launch {
        val entry = repository.getById(id) ?: return@launch
        val newPwd = PasswordGenerator.generate(16, true, true, true).password
        val updated = entry.addToPasswordHistory(entry.password).copy(
            encryptedPassword = CryptoUtils.encrypt(newPwd),
            lastChanged = System.currentTimeMillis()
        )
        repository.update(updated)
    }

    // ✅ НОВЫЙ МЕТОД: массовая ротация
    fun bulkRotatePasswords(ids: List<String>) = viewModelScope.launch {
        ids.forEach { rotatePassword(it) }
    }
}
