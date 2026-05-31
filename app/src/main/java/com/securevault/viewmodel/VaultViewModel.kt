package com.securevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.data.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {

    //  Текущий выбранный фильтр (по умолчанию: ВСЕ)
    private val _currentFilter = MutableStateFlow<Profile?>(null)
    val currentFilter: StateFlow<Profile?> = _currentFilter.asStateFlow()

    //  Список записей (обновляется при смене фильтра)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    init {
        loadEntries()
    }

    //  Загрузка с учётом фильтра
    fun setFilter(profile: Profile?) {
        _currentFilter.value = profile
        loadEntries()
    }

    private fun loadEntries() = viewModelScope.launch {
        val flow = when (val profile = _currentFilter.value) {
            null -> repository.allEntries          // Все записи
            else -> repository.getEntriesByProfile(profile) // Только профиль
        }
        flow.collect { _entries.value = it }
    }

    fun insert(entry: Entry) = viewModelScope.launch { repository.insert(entry) }
    fun update(entry: Entry) = viewModelScope.launch { repository.update(entry) }
    fun delete(entry: Entry) = viewModelScope.launch { repository.delete(entry) }
    fun deleteById(id: String) = viewModelScope.launch { repository.deleteById(id) }

        //  Поток просроченных записей (для отдельного экрана/уведомлений)
    val expiredEntries: Flow<List<Entry>> = repository.allEntries
        .map { entries -> entries.filter { it.isPasswordExpired() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    //  Проверка: есть ли записи, требующие внимания
    fun getEntriesNeedingAttention(): Flow<List<Entry>> = repository.allEntries
        .map { entries -> 
            entries.filter { 
                it.isPasswordExpired() || it.getDaysUntilExpiry() <= 7 
            } 
        }
    
    // Обновить дату последнего изменения (после смены пароля)
    fun markPasswordChanged(entryId: String) = viewModelScope.launch {
        val entry = repository.getById(entryId) ?: return@launch
        repository.update(entry.copy(
            lastChanged = System.currentTimeMillis(),
            failedAttempts = 0  // Сбросить счётчик попыток при успешной смене
        ))
    }
}
