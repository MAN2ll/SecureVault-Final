package com.securevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.VaultRepository
import com.securevault.utils.CryptoUtils
import com.securevault.utils.MnemonicPasswordGenerator
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

    // ✅ ИСПРАВЛЕНО: корректная ротация с обновлением даты
    fun rotatePassword(id: String) = viewModelScope.launch {
        val entry = repository.getById(id) ?: return@launch
        
        val newPassword: String
        val newHint: String?
        
        if (entry.generationType == "mnemonic" && entry.textHint != null) {
            // Для мнемонических паролей — перегенерация с новой датой ротации
            val params = MnemonicPasswordGenerator.GenerationParams(
                phrase = entry.textHint,
                serviceName = entry.service,
                rotationMonth = null, // использовать текущий
                rotationYear = null,
                targetLength = 16,
                includeLeet = true,
                includeServiceCode = true,
                includeRotationCode = true
            )
            val result = MnemonicPasswordGenerator.generate(params)
            newPassword = result.password
            newHint = result.mnemonicHint
        } else {
            // Для обычных паролей — случайная генерация
            newPassword = PasswordGenerator.generate(16, true, true, true).password
            newHint = entry.textHint
        }
        
        val now = System.currentTimeMillis()
        val newNextRotationDate = now + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
        
        val updated = entry.addToPasswordHistory(entry.password, entry.generationType).copy(
            encryptedPassword = CryptoUtils.encrypt(newPassword),
            textHint = newHint,
            lastChanged = now,
            nextRotationDate = newNextRotationDate
        )
        repository.update(updated)
    }

    fun bulkRotatePasswords(ids: List<String>) = viewModelScope.launch {
        ids.forEach { rotatePassword(it) }
    }
}
