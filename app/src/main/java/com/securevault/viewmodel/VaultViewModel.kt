package com.securevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.data.VaultRepository
import com.securevault.utils.CryptoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {

    // Текущий фильтр профиля
    private val _currentFilter = MutableStateFlow<Profile?>(null)
    val currentFilter: StateFlow<Profile?> = _currentFilter.asStateFlow()

    // Список записей
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
        flow.collect { _entries.value = it }
    }

    // Поток записей, требующих ротации
    val entriesNeedingRotation: StateFlow<List<Entry>> = repository.allEntries
        .map { entries ->
            entries.filter { it.rotationEnabled && it.shouldRotateBySchedule() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Поток просроченных записей
    val expiredEntries: StateFlow<List<Entry>> = repository.allEntries
        .map { entries -> entries.filter { it.isPasswordExpired() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Вставка новой записи
    fun insert(entry: Entry) = viewModelScope.launch { 
        repository.insert(entry) 
    }

    // Обновление записи
    fun update(entry: Entry) = viewModelScope.launch { 
        repository.update(entry) 
    }

    // Удаление записи
    fun delete(entry: Entry) = viewModelScope.launch { 
        repository.delete(entry) 
    }

    fun deleteById(id: String) = viewModelScope.launch { 
        repository.deleteById(id) 
    }

    // Массовое обновление паролей (ротация)
    fun bulkRotatePasswords(
        entryIds: List<String>,
        generateNewPassword: (Entry) -> String
    ) = viewModelScope.launch {
        entryIds.forEach { id ->
            val entry = repository.getById(id) ?: return@forEach
            
            // Генерируем новый пароль
            val newPassword = generateNewPassword(entry)
            
            // Проверяем уникальность относительно истории
            if (entry.isPasswordInHistory(newPassword)) {
                // Если пароль не уникален, можно добавить логику повторной генерации
                // Здесь для простоты пропускаем или логируем
                return@forEach
            }
            
            // Хешируем старый пароль для истории
            val oldHash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(entry.password.toByteArray())
                .joinToString("") { "%02x".format(it) }
            
            // Шифруем новый пароль
            val newEncryptedPassword = CryptoUtils.encrypt(newPassword)
            
            // Обновляем запись: новый пароль, обновлённая история, новая дата ротации
            val updatedEntry = entry.copy(
                encryptedPassword = newEncryptedPassword,
                lastChanged = System.currentTimeMillis(),
                failedAttempts = 0
            ).addToHistory(oldHash)
            
            repository.update(updatedEntry)
        }
    }
    
    // Получение записей, истекающих в ближайшие N дней
    fun getEntriesExpiringSoon(days: Int = 30): StateFlow<List<Entry>> {
        return repository.allEntries
            .map { entries ->
                entries.filter { entry ->
                    val daysUntilExpiry = entry.getDaysUntilExpiry()
                    daysUntilExpiry <= days && daysUntilExpiry >= 0
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }
}
