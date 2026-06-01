package com.securevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.data.VaultRepository
import com.securevault.utils.CryptoUtils // ✅ Добавь этот импорт
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

    val entries: StateFlow<List<Entry>> = repository.allEntries
        .map { list ->
            when (val profile = _currentFilter.value) {
                null -> list
                else -> list.filter { it.profile == profile }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(profile: Profile?) { _currentFilter.value = profile }
    fun insert(entry: Entry) = viewModelScope.launch { repository.insert(entry) }
    fun update(entry: Entry) = viewModelScope.launch { repository.update(entry) }
    fun delete(entry: Entry) = viewModelScope.launch { repository.delete(entry) }

    // ✅ Методы для ротации (добавлены)
    fun rotatePassword(id: String) = viewModelScope.launch {
        val entry = repository.getById(id) ?: return@launch
        val newPassword = PasswordGenerator.generate(
            length = 16,
            useUpper = true,
            useDigits = true,
            useSpecial = true
        ).password
        
        val updated = entry.copy(
            encryptedPassword = CryptoUtils.encrypt(newPassword),
            lastChanged = System.currentTimeMillis(),
            nextRotationDate = if (entry.rotationEnabled) {
                System.currentTimeMillis() + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
            } else null
        )
        repository.update(updated)
    }

    fun bulkRotatePasswords(ids: List<String>) = viewModelScope.launch {
        ids.forEach { id -> rotatePassword(id) }
    }
}
