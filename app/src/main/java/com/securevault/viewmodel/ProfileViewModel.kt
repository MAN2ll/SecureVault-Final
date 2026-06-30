package com.securevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Profile
import com.securevault.data.VaultRepository
import com.securevault.utils.CryptoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insert(name: String, password: String) = viewModelScope.launch {
        val hash = CryptoUtils.hashPassword(password) // Используем тот же хэш что и мастер-пароль
        repository.insertProfile(Profile(name = name, passwordHash = hash))
    }

    fun delete(id: Int) = viewModelScope.launch {
        repository.deleteProfile(id)
    }

    fun verifyPassword(profile: Profile, password: String): Boolean {
        return profile.passwordHash == CryptoUtils.hashPassword(password)
    }
}
