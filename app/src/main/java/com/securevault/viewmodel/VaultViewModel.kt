package com.securevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.Profile
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

    private val _profileFilter = MutableStateFlow<Profile?>(null)
    val profileFilter: StateFlow<Profile?> = _profileFilter.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    val entries: StateFlow<List<Entry>> = repository.allEntries
        .combine(_profileFilter) { list, profile ->
            if (profile == null) list else list.filter { it.profile == profile }
        }
        .combine(_favoritesOnly) { list, favOnly ->
            if (favOnly) list.filter { it.isFavorite } else list
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setProfileFilter(profile: Profile?) { _profileFilter.value = profile }
    fun toggleFavoritesOnly() { _favoritesOnly.value = !_favoritesOnly.value }

    fun insert(entry: Entry) = viewModelScope.launch { repository.insert(entry) }
    fun update(entry: Entry) = viewModelScope.launch { repository.update(entry) }
    fun delete(entry: Entry) = viewModelScope.launch { repository.delete(entry) }
    fun deleteAll() = viewModelScope.launch { repository.deleteAll() }

    fun toggleFavorite(entry: Entry) = viewModelScope.launch {
        repository.update(entry.copy(isFavorite = !entry.isFavorite))
    }

    // ✅ ВОЗВРАЩЁННЫЙ МЕТОД
    fun updatePassword(id: String, newPassword: String) = viewModelScope.launch {
        val entry = repository.getById(id) ?: return@launch
        val updated = entry.copy(
            encryptedPassword = CryptoUtils.encrypt(newPassword),
            lastChanged = System.currentTimeMillis(),
            nextRotationDate = if (entry.rotationEnabled) System.currentTimeMillis() + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000) else null
        )
        repository.update(updated)
    }

    fun rotatePassword(id: String) = viewModelScope.launch {
        val entry = repository.getById(id) ?: return@launch
        val newPwd = PasswordGenerator.generate(16, true, true, true).password
        val updated = entry.copy(
            encryptedPassword = CryptoUtils.encrypt(newPwd),
            lastChanged = System.currentTimeMillis(),
            nextRotationDate = if (entry.rotationEnabled) System.currentTimeMillis() + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000) else null
        )
        repository.update(updated)
    }

    fun bulkRotatePasswords(ids: List<String>) = viewModelScope.launch {
        ids.forEach { rotatePassword(it) }
    }
}
