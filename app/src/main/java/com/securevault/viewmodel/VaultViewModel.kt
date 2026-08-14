package com.securevault.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.Entry
import com.securevault.data.PasswordHistoryItem
import com.securevault.data.Profile
import com.securevault.data.VaultRepository
import com.securevault.security.ProfilePasswordHasher
import com.securevault.utils.BackupData
import com.securevault.utils.BackupManager
import com.securevault.utils.CryptoUtils
import com.securevault.utils.ImportMode
import com.securevault.utils.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    application: Application,
    private val repository: VaultRepository
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _currentProfileId = MutableStateFlow<Int?>(null)
    val currentProfileId: StateFlow<Int?> = _currentProfileId.asStateFlow()

    val entries = repository.allEntries
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val profiles = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val currentProfile: StateFlow<Profile?> = combine(
        profiles, currentProfileId
    ) { profileList, id ->
        profileList.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    init {
        viewModelScope.launch {
            profiles.collect { list ->
                if (_currentProfileId.value == null && list.isNotEmpty()) {
                    _currentProfileId.value = list.first().id
                }
            }
        }
    }

    fun setCurrentProfile(profileId: Int) {
        _currentProfileId.value = profileId
    }

    fun findEntryById(entryId: String): Entry? {
        return entries.value.find { it.id == entryId }
    }

    fun addEntry(entry: Entry) {
        viewModelScope.launch {
            repository.insertEntry(entry)
        }
    }

    fun updateEntry(entry: Entry) {
        viewModelScope.launch {
            repository.updateEntry(entry)
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            repository.deleteEntry(entryId)
        }
    }

    fun toggleFavorite(entryId: String) {
        viewModelScope.launch {
            val current = entries.value.find { it.id == entryId } ?: return@launch
            repository.updateEntry(current.copy(isFavorite = !current.isFavorite))
        }
    }

    fun addPasswordToHistory(entry: Entry, oldEncryptedPassword: String?, type: String, relatedService: String? = null) {
        viewModelScope.launch {
            val history = entry.getPasswordHistory().toMutableList()
            history.add(
                PasswordHistoryItem(
                    date = System.currentTimeMillis(),
                    encryptedOldPassword = oldEncryptedPassword,
                    type = type,
                    relatedService = relatedService
                )
            )
            val updated = entry.copy(
                passwordHistoryJson = com.securevault.utils.JsonUtils.toJson(history),
                lastChanged = System.currentTimeMillis()
            )
            repository.updateEntry(updated)
        }
    }

    fun addProfile(profile: Profile) {
        viewModelScope.launch {
            repository.insertProfile(profile)
        }
    }

    fun updateProfile(profile: Profile) {
        viewModelScope.launch {
            repository.updateProfile(profile)
        }
    }

    fun deleteProfile(profileId: Int) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
        }
    }

    fun setProfilePin(profileId: Int, pin: String) {
        viewModelScope.launch {
            val profile = profiles.value.find { it.id == profileId } ?: return@launch
            val hashResult = ProfilePasswordHasher.hash(pin)
            val updated = profile.copy(
                pinHash = hashResult.hash,
                pinSalt = hashResult.salt,
                pinIterations = hashResult.iterations
            )
            repository.updateProfile(updated)
        }
    }

    fun verifyProfilePin(profileId: Int, pin: String): Boolean {
        val profile = profiles.value.find { it.id == profileId } ?: return false
        val hash = profile.pinHash ?: return false
        val salt = profile.pinSalt ?: return false
        val iterations = profile.pinIterations
        return ProfilePasswordHasher.verify(pin, hash, salt, iterations)
    }

    fun bulkReplacePasswords(replacements: List<BulkPasswordReplacement>) {
        viewModelScope.launch {
            val currentEntries = entries.value
            for (replacement in replacements) {
                val entry = currentEntries.find { it.id == replacement.entryId } ?: continue
                val oldEncrypted = entry.password
                
                val newEncrypted = withContext(Dispatchers.Default) {
                    CryptoUtils.encrypt(replacement.newPassword)
                }
                
                val history = entry.getPasswordHistory().toMutableList()
                history.add(
                    PasswordHistoryItem(
                        date = System.currentTimeMillis(),
                        encryptedOldPassword = oldEncrypted,
                        type = replacement.generationType,
                        relatedService = null
                    )
                )
                
                val updated = entry.copy(
                    password = newEncrypted,
                    passwordHistoryJson = com.securevault.utils.JsonUtils.toJson(history),
                    lastChanged = System.currentTimeMillis(),
                    textHint = replacement.textHint ?: entry.textHint,
                    mnemonicPhraseHint = replacement.mnemonicPhraseHint ?: entry.mnemonicPhraseHint,
                    mnemonicOptionsJson = replacement.mnemonicOptionsJson ?: entry.mnemonicOptionsJson
                )
                repository.updateEntry(updated)
            }
        }
    }

    suspend fun exportAllProfiles(): BackupData {
        return BackupManager.exportAllProfiles(repository, appContext)
    }

    suspend fun importBackup(
        backupData: BackupData,
        mode: ImportMode,
        newPin: String?
    ): ImportResult {
        return BackupManager.importBackup(repository, backupData, mode, newPin, appContext)
    }
}

data class BulkPasswordReplacement(
    val entryId: String,
    val newPassword: String,
    val generationType: String,
    val textHint: String? = null,
    val mnemonicPhraseHint: String? = null,
    val mnemonicOptionsJson: String? = null
)

data class PasswordShuffleAssignment(
    val entryId: String,
    val newPassword: String,
    val generationType: String,
    val targetProfileId: Int? = null
)
