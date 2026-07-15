package com.securevault.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Profile
import com.securevault.data.VaultRepository
import com.securevault.security.ProfilePasswordHasher
import com.securevault.utils.AccessMode
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

    fun insertProfile(name: String, pin: String?, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try {
                val (hash, salt) = if (!pin.isNullOrBlank()) {
                    val s = ProfilePasswordHasher.generateSalt()
                    ProfilePasswordHasher.hash(pin, s) to s
                } else {
                    "" to ""
                }
                
                // Явное задание режима доступа в зависимости от наличия PIN
                val accessMode = if (pin.isNullOrBlank()) {
                    AccessMode.NO_CONFIRMATION.value
                } else {
                    AccessMode.PIN_REQUIRED.value
                }

                val profile = Profile(
                    name = name, 
                    passwordHash = hash, 
                    passwordSalt = salt,
                    passwordAccessMode = accessMode
                )
                repository.insertProfile(profile)
                onResult(PasswordOperationResult.Success)
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun verifyPassword(profile: Profile, pin: String): Boolean {
        return ProfilePasswordHasher.verify(pin, profile.passwordHash, profile.passwordSalt)
    }

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

    //  При установке PIN режим становится PIN_REQUIRED
    fun setProfilePin(profile: Profile, newPin: String, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try {
                val salt = ProfilePasswordHasher.generateSalt()
                val hash = ProfilePasswordHasher.hash(newPin, salt)
                repository.updateProfile(profile.copy(
                    passwordHash = hash, 
                    passwordSalt = salt,
                    passwordAccessMode = AccessMode.PIN_REQUIRED.value
                ))
                onResult(PasswordOperationResult.Success)
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
        }
    }

    //  При удалении PIN режим становится NO_CONFIRMATION
    fun removeProfilePin(profile: Profile, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try {
                repository.updateProfile(profile.copy(
                    passwordHash = "", 
                    passwordSalt = "",
                    passwordAccessMode = AccessMode.NO_CONFIRMATION.value
                ))
                onResult(PasswordOperationResult.Success)
            } catch (e: Exception) {
                onResult(PasswordOperationResult.Error("Ошибка: ${e.message}"))
            }
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
