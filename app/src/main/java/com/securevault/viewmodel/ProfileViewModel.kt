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
    application: Application
) : AndroidViewModel(application) {

    val profiles: StateFlow<List<Profile>> = repository.allProfiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun insertProfile(name: String, password: String, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try {
                val salt = ProfilePasswordHasher.generateSalt()
                val hash = ProfilePasswordHasher.hash(password, salt)
                val profile = Profile(name = name, passwordHash = hash, passwordSalt = salt)
                repository.insertProfile(profile)
                onResult(PasswordOperationResult.Success)
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    //  Для совместимости с ProfileListScreen
    fun insert(name: String, pin: String) {
        insertProfile(name, pin) { /* результат игнорируется в этом контексте */ }
    }

    //  Для совместимости с ProfileListScreen
    fun verifyPassword(profile: Profile, pin: String): Boolean {
        return ProfilePasswordHasher.verify(pin, profile.passwordHash, profile.passwordSalt)
    }

    //  Для совместимости с ProfileListScreen (используем getByProfileId)
    fun hasEntriesInProfile(profileId: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val entries = repository.getByProfileId(profileId)
            onResult(entries.isNotEmpty())
        }
    }

    fun updateProfile(profile: Profile) {
        viewModelScope.launch {
            repository.updateProfile(profile)
        }
    }

    fun deleteProfile(profileId: Int, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try {
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
}
