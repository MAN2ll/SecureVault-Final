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

    //  Текущий выбранный фильтр
    private val _currentFilter = MutableStateFlow<Profile?>(null)
    val currentFilter: StateFlow<Profile?> = _currentFilter.asStateFlow()

    //  Список записей
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

    //  Просроченные записи (исправлено: добавлен initial)
    val expiredEntries: StateFlow<List<Entry>> = repository.allEntries
        .map { entries -> entries.filter { it.isPasswordExpired() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    //  Записи, требующие внимания
    fun getEntriesNeedingAttention(): StateFlow<List<Entry>> = repository.allEntries
        .map { entries -> 
            entries.filter { 
                it.isPasswordExpired() || it.getDaysUntilExpiry() <= 7 
            } 
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    //  Обновить дату последнего изменения
    fun markPasswordChanged(entryId: String) = viewModelScope.launch {
        val entry = repository.getById(entryId) ?: return@launch
        repository.update(entry.copy(
            lastChanged = System.currentTimeMillis(),
            failedAttempts = 0
        ))
    }

    fun insert(entry: Entry) = viewModelScope.launch { repository.insert(entry) }
    fun update(entry: Entry) = viewModelScope.launch { repository.update(entry) }
    fun delete(entry: Entry) = viewModelScope.launch { repository.delete(entry) }
    fun deleteById(id: String) = viewModelScope.launch { repository.deleteById(id) }
}
