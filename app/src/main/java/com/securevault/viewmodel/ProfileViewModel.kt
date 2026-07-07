package com.securevault.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Profile
import com.securevault.data.VaultRepository
import com.securevault.security.ProfilePasswordHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val appContext: Application
) : AndroidViewModel(appContext) {

    val profiles: StateFlow<List<Profile>> = repository.allProfiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun insert(name: String, password: String) {
        viewModelScope.launch {
            val salt = ProfilePasswordHasher.generateSalt()
            val hash = ProfilePasswordHasher.hash(password, salt)
            val profile = Profile(name = name, passwordHash = hash, passwordSalt = salt)
            repository.insertProfile(profile)
        }
    }

    fun verifyPassword(profile: Profile, password: String): Boolean {
        return ProfilePasswordHasher.verify(password, profile.passwordHash, profile.passwordSalt)
    }

    fun updateProfile(profile: Profile) {
        viewModelScope.launch {
            repository.updateProfile(profile)
        }
    }

    // ✅ НОВЫЙ МЕТОД: Проверка, есть ли записи в профиле
    fun hasEntriesInProfile(profileId: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val entries = repository.getByProfileId(profileId)
            onResult(entries.isNotEmpty())
        }
    }

    // ✅ НОВЫЙ МЕТОД: Удаление профиля с проверкой
    fun deleteProfile(
        profileId: Int,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Проверяем, есть ли записи
                val entries = repository.getByProfileId(profileId)
                if (entries.isNotEmpty()) {
                    onResult(PasswordOperationResult.Error(
                        "Нельзя удалить профиль, пока в нём есть пароли. Сначала удалите все пароли профиля."
                    ))
                    return@launch
                }
                repository.deleteProfile(profileId)
                onResult(PasswordOperationResult.Success)
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    //  Удаление всех записей профиля
    fun deleteAllEntriesInProfile(
        profileId: Int,
        onResult: (PasswordOperationResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteEntriesByProfileId(profileId)
                onResult(PasswordOperationResult.Success)
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }
}
