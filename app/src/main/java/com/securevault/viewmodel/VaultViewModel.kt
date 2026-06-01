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

    private val _currentFilter = MutableStateFlow<Profile?>(null)
    val currentFilter: StateFlow<Profile?> = _currentFilter.asStateFlow()

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    init {
        loadEntries()
    }

    fun setFilter(profile: Profile?) {
        _currentFilter.value = profile
        loadEntries()
    }

    private fun loadEntries() = viewModelScope.launch {
        val flow = when (val profile = _currentFilter.value) {
            null -> repository.allEntries
            else -> repository.getEntriesByProfile(profile)
        }
        flow.collect { list ->
            _entries.value = list
        }
    }

    fun insert(entry: Entry) = viewModelScope.launch {
        repository.insert(entry)
    }

    fun update(entry: Entry) = viewModelScope.launch {
        repository.update(entry)
    }

    fun delete(entry: Entry) = viewModelScope.launch {
        repository.delete(entry)
    }

    // === РОТАЦИЯ ПАРОЛЕЙ ===

    fun rotatePassword(id: String) = viewModelScope.launch {
        val entry = repository.getById(id) ?: return@launch
        
        val newPassword = PasswordGenerator.generate(
            PasswordGenerator.GeneratorOptions(
                length = 16,
                useUppercase = true,
                useDigits = true,
                useSpecial = true
            )
        ).password

        val newEncryptedPassword = com.securevault.utils.CryptoUtils.encrypt(newPassword)
        val periodMillis = entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000
        
        val updatedEntry = entry.copy(
            encryptedPassword = newEncryptedPassword,
            lastChanged = System.currentTimeMillis(),
            nextRotationDate = System.currentTimeMillis() + periodMillis,
            failedAttempts = 0
        )

        repository.update(updatedEntry)
    }

    fun bulkRotatePasswords(ids: List<String>) = viewModelScope.launch {
        ids.forEach { id -> rotatePassword(id) }
    }
}
