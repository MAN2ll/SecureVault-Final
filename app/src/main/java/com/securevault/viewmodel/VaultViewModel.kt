package com.securevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.data.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository  //  Правильный тип
) : ViewModel() {

    val allEntries: Flow<List<Entry>> = repository.allEntries
    val favoriteEntries: Flow<List<Entry>> = repository.favoriteEntries
    
    //  Поиск
    fun search(query: String): Flow<List<Entry>> = repository.searchByService("%$query%")
    
    //  Фильтр по профилю
    fun getByProfile(profile: Profile): Flow<List<Entry>> = repository.getByProfile(profile)
    
    // Просроченные пароли
    val expiredPasswords: Flow<List<Entry>> = repository.getExpiredPasswords()

    // Метод создания с шифрованием
    fun createEntry(
        service: String,
        username: String,
        password: String,
        category: String = "general",
        profile: Profile = Profile.PERSONAL,
        notes: String = "",
        changeIntervalDays: Int = 90
    ) = viewModelScope.launch {
        repository.createEntry(service, username, password, category, profile, notes, changeIntervalDays)
    }
    
    fun updateEntry(entry: Entry) = viewModelScope.launch { repository.update(entry) }
    fun deleteEntry(entry: Entry) = viewModelScope.launch { repository.delete(entry) }
    fun deleteById(id: String) = viewModelScope.launch { repository.deleteById(id) }
    
    //  Инкремент попыток подбора
    fun incrementFailedAttempts(entryId: String) = viewModelScope.launch {
        repository.incrementFailedAttempts(entryId)
    }
    
    //  Сброс попыток после успешного входа
    fun resetFailedAttempts(entryId: String) = viewModelScope.launch {
        repository.resetFailedAttempts(entryId)
    }
}
