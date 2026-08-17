@file:OptIn(ExperimentalMaterial3Api::dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.BackupData
import com.securevault.data.Entry
import com.securevault.data.PasswordHistoryItem
import com.securevault.data.Profile
import com.securevault.data.VaultRepository
import com.securevault.security.ProfilePasswordHasher
import com.securevault.utils.BackupManager
import com.securevault.utils.CryptoUtils
import com.securevault.utils.ImportMode
import com.securevault.utils.ImportResult
import com.securevault.utils.JsonUtils
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

    val allEntries: StateFlow<List<Entry>> = repository.allEntries
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val entries: StateFlow<List<Entry>> = combine(
        allEntries, currentProfileId
    ) { entries, profileId ->
        if (profileId != null) entries.filter { it.profileId == profileId } else entries
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val rotationEntries: StateFlow<List<Entry>> = combine(
        allEntries, currentProfileId
    ) { entries, profileId ->
        val filtered = if (profileId != null) entries.filter { it.profileId == profileId } else entries
        filtered.filter { it.rotationEnabled && it.nextRotationDate != null && it.nextRotationDate <= System.currentTimeMillis() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val profiles: StateFlow<List<Profile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val currentProfile: StateFlow<Profile?> = combine(profiles, currentProfileId) { profileList, id ->
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

    fun setCurrentProfile(profileId: Int) { _currentProfileId.value = profileId }
    fun findEntryById(entryId: String): Entry? = allEntries.value.find { it.id == entryId }

    fun insert(entry: Entry, onResult: (PasswordOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.insert(entry)
                onResult(PasswordOperationResult.Success("Запись добавлена"))
            } catch (e: Exception) { onResult(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
        }
    }

    fun insertEntry(entry: Entry, onResult: (PasswordOperationResult) -> Unit) = insert(entry, onResult)

    fun updateEntry(entry: Entry, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try {
                repository.update(entry)
                onResult(PasswordOperationResult.Success("Запись обновлена"))
            } catch (e: Exception) { onResult(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
        }
    }

    fun deleteEntry(entryId: String, onResult: (PasswordOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val entry = allEntries.value.find { it.id == entryId }
                if (entry != null) repository.delete(entry)
                onResult(PasswordOperationResult.Success("Запись удалена"))
            } catch (e: Exception) { onResult(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
        }
    }

    fun addToPasswordHistory(
        entry: Entry, oldEncryptedPassword: String?, type: String, relatedService: String? = null,
        onResult: (PasswordOperationResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val history = entry.getPasswordHistory().toMutableList()
                history.add(PasswordHistoryItem(System.currentTimeMillis(), oldEncryptedPassword, type, relatedService))
                repository.update(entry.copy(passwordHistoryJson = JsonUtils.toJson(history), lastChanged = System.currentTimeMillis()))
                onResult(PasswordOperationResult.Success())
            } catch (e: Exception) { onResult(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
        }
    }

    //  ПЕРЕГРУЗКА 1: По Entry, только пароль
    fun replacePassword(entry: Entry, newPassword: String, generationType: String, onResult: (PasswordOperationResult) -> Unit) {
        replacePasswordInternal(entry.id, newPassword, generationType, null, generationType, null, null, onResult)
    }

    //  ПЕРЕГРУЗКА 2: По Entry, с метаданными (для ReminderScreen)
    fun replacePassword(
        entry: Entry, newPassword: String, newHint: String?, newGenerationType: String,
        newMnemonicPhraseHint: String?, newMnemonicOptionsJson: String?, onResult: (PasswordOperationResult) -> Unit
    ) {
        replacePasswordInternal(entry.id, newPassword, newGenerationType, newHint, newGenerationType, newMnemonicPhraseHint, newMnemonicOptionsJson, onResult)
    }

    // ПЕРЕГРУЗКА 3: По ID, только пароль
    fun replacePassword(entryId: String, newPassword: String, generationType: String, onResult: (PasswordOperationResult) -> Unit) {
        replacePasswordInternal(entryId, newPassword, generationType, null, generationType, null, null, onResult)
    }

    //  ПЕРЕГРУЗКА 4: По ID, с метаданными (для RotationScreen)
    fun replacePassword(
        entryId: String, newPassword: String, newHint: String?, newGenerationType: String,
        newMnemonicPhraseHint: String?, newMnemonicOptionsJson: String?, onResult: (PasswordOperationResult) -> Unit
    ) {
        replacePasswordInternal(entryId, newPassword, newGenerationType, newHint, newGenerationType, newMnemonicPhraseHint, newMnemonicOptionsJson, onResult)
    }

    //  ПЕРЕГРУЗКА 5: Только метаданные (без смены пароля)
    fun replacePassword(
        entryId: String, newHint: String?, newGenerationType: String,
        newMnemonicPhraseHint: String?, newMnemonicOptionsJson: String?, onResult: (PasswordOperationResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val entry = allEntries.value.find { it.id == entryId } ?: return@launch
                repository.update(entry.copy(
                    textHint = newHint, generationType = newGenerationType,
                    mnemonicPhraseHint = newMnemonicPhraseHint, mnemonicOptionsJson = newMnemonicOptionsJson,
                    lastChanged = System.currentTimeMillis()
                ))
                onResult(PasswordOperationResult.Success("Параметры обновлены"))
            } catch (e: Exception) { onResult(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
        }
    }

    private fun replacePasswordInternal(
        entryId: String, newPassword: String, actualGenerationType: String,
        newHint: String?, newGenerationType: String, newMnemonicPhraseHint: String?,
        newMnemonicOptionsJson: String?, onResult: (PasswordOperationResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val entry = allEntries.value.find { it.id == entryId } ?: return@launch
                val newEncrypted = withContext(Dispatchers.Default) { CryptoUtils.encrypt(newPassword) }
                val history = entry.getPasswordHistory().toMutableList()
                history.add(PasswordHistoryItem(System.currentTimeMillis(), entry.encryptedPassword, actualGenerationType, null))
                
                repository.update(entry.copy(
                    encryptedPassword = newEncrypted,
                    passwordHistoryJson = JsonUtils.toJson(history),
                    lastChanged = System.currentTimeMillis(),
                    nextRotationDate = if (entry.rotationEnabled) System.currentTimeMillis() + (entry.rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000) else null,
                    textHint = newHint,
                    generationType = newGenerationType,
                    mnemonicPhraseHint = newMnemonicPhraseHint,
                    mnemonicOptionsJson = newMnemonicOptionsJson
                ))
                onResult(PasswordOperationResult.Success("Пароль заменён"))
            } catch (e: Exception) { onResult(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
        }
    }

    fun applyManagedShuffle(assignments: Map<String, String?>, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try {
                val currentEntries = allEntries.value
                var successCount = 0
                for ((targetId, sourceId) in assignments) {
                    val target = currentEntries.find { it.id == targetId } ?: continue
                    val source = currentEntries.find { it.id == sourceId } ?: continue
                    val newEncrypted = withContext(Dispatchers.Default) { CryptoUtils.encrypt(CryptoUtils.decrypt(source.encryptedPassword)) }
                    val history = target.getPasswordHistory().toMutableList()
                    history.add(PasswordHistoryItem(System.currentTimeMillis(), target.encryptedPassword, "shuffle", source.service))
                    repository.update(target.copy(
                        encryptedPassword = newEncrypted, passwordHistoryJson = JsonUtils.toJson(history),
                        lastChanged = System.currentTimeMillis(), generationType = source.generationType,
                        mnemonicPhraseHint = source.mnemonicPhraseHint, mnemonicOptionsJson = source.mnemonicOptionsJson
                    ))
                    successCount++
                }
                onResult(PasswordOperationResult.Success("Перекрёстная ротация выполнена: $successCount записей"))
            } catch (e: Exception) { onResult(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
        }
    }

    fun bulkReplacePasswords(replacements: List<BulkPasswordReplacement>, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try {
                val currentEntries = allEntries.value
                for (rep in replacements) {
                    val entry = currentEntries.find { it.id == rep.entryId } ?: continue
                    val newEncrypted = withContext(Dispatchers.Default) { CryptoUtils.encrypt(rep.newPassword) }
                    val history = entry.getPasswordHistory().toMutableList()
                    history.add(PasswordHistoryItem(System.currentTimeMillis(), entry.encryptedPassword, rep.generationType, null))
                    repository.update(entry.copy(
                        encryptedPassword = newEncrypted, passwordHistoryJson = JsonUtils.toJson(history),
                        lastChanged = System.currentTimeMillis(), textHint = rep.textHint ?: entry.textHint,
                        mnemonicPhraseHint = rep.mnemonicPhraseHint ?: entry.mnemonicPhraseHint,
                        mnemonicOptionsJson = rep.mnemonicOptionsJson ?: entry.mnemonicOptionsJson
                    ))
                }
                onResult(PasswordOperationResult.Success("Пароли заменены"))
            } catch (e: Exception) { onResult(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
        }
    }

    fun addProfile(profile: Profile, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try { repository.insertProfile(profile); onResult(PasswordOperationResult.Success("Профиль добавлен")) }
            catch (e: Exception) { onResult(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
        }
    }

    fun updateProfile(profile: Profile, onResult: (PasswordOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            try { repository.updateProfile(profile); onResult(PasswordOperationResult.Success()) }
            catch (e: Exception) { onResult(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
        }
    }

    fun deleteProfile(profileId: Int, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try { repository.deleteProfile(profileId); onResult(PasswordOperationResult.Success("Профиль удалён")) }
            catch (e: Exception) { onResult(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
        }
    }

    fun setProfilePin(profileId: Int, pin: String, onResult: (PasswordOperationResult) -> Unit) {
        viewModelScope.launch {
            try {
                val profile = profiles.value.find { it.id == profileId } ?: return@launch
                val salt = ProfilePasswordHasher.generateSalt()
                repository.updateProfile(profile.copy(passwordHash = ProfilePasswordHasher.hash(pin, salt), passwordSalt = salt))
                onResult(PasswordOperationResult.Success("PIN установлен"))
            } catch (e: Exception) { onResult(PasswordOperationResult.Error("Ошибка: ${e.message}")) }
        }
    }

    fun verifyProfilePin(profileId: Int, pin: String): Boolean {
        val profile = profiles.value.find { it.id == profileId } ?: return false
        return profile.passwordHash?.let { ProfilePasswordHasher.verify(pin, it, profile.passwordSalt ?: "") } ?: false
    }

    suspend fun exportAllProfiles(): BackupData = BackupManager.exportAllProfiles(repository, appContext)
    suspend fun importBackup(backupData: BackupData, mode: ImportMode, newPin: String?): ImportResult =
        BackupManager.importBackup(repository, backupData, mode, newPin, appContext)
}

data class BulkPasswordReplacement(
    val entryId: String, val newPassword: String, val generationType: String,
    val textHint: String? = null, val mnemonicPhraseHint: String? = null, val mnemonicOptionsJson: String? = null
)

data class PasswordShuffleAssignment(
    val entryId: String, val newPassword: String, val generationType: String, val targetProfileId: Int? = null
)
