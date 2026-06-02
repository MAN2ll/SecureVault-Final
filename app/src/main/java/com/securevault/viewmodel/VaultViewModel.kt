package com.securevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.data.VaultRepository
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

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    val entries: StateFlow<List<Entry>> = repository.allEntries
        .map { list ->
            var filtered = list
            if (_favoritesOnly.value) filtered = filtered.filter { it.isFavorite }
            _profileFilter.value?.let { p -> filtered = filtered.filter { it.profile == p } }
            _categoryFilter.value?.let { c -> filtered = filtered.filter { it.category == c } }
            filtered
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Действия
    fun setProfileFilter(profile: Profile?) { _profileFilter.value = profile; _categoryFilter.value = null }
    fun setCategoryFilter(category: String?) { _categoryFilter.value = category }
    fun toggleFavoritesOnly() { _favoritesOnly.value = !_favoritesOnly.value }

    fun insert(entry: Entry) = viewModelScope.launch { repository.insert(entry) }
    fun update(entry: Entry) = viewModelScope.launch { repository.update(entry) }
    fun delete(entry: Entry) = viewModelScope.launch { repository.delete(entry) }
    fun deleteAll() = viewModelScope.launch { repository.deleteAll() }

    fun toggleFavorite(entry: Entry) = viewModelScope.launch {
        repository.update(entry.copy(isFavorite = !entry.isFavorite))
    }

    // Ротация
    fun rotatePassword(id: String) = viewModelScope.launch {
        val entry = repository.getById(id) ?: return@launch
        val newPwd = PasswordGenerator.generate(16, true, true, true).password
        val updated = entry.copy(
            encryptedPassword = com.securevault.utils.CryptoUtils.encrypt(newPwd),
            lastChanged = System.currentTimeMillis(),
            nextRotationDate = if (entry.rotationEnabled) System.currentTimeMillis() + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000) else null
        )
        repository.update(updated)
    }

    fun bulkRotatePasswords(ids: List<String>) = viewModelScope.launch {
        ids.forEach { rotatePassword(it) }
    }
}
