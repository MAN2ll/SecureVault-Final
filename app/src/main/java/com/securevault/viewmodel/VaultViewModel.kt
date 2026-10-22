package com.securevault.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.*
import com.securevault.security.ProfilePasswordHasher
import com.securevault.utils.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(application: Application,
    private val repository: VaultRepository) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    companion object { private val writes = Mutex() }
    private val _currentProfileId = MutableStateFlow<Int?>(null)
    val currentProfileId = _currentProfileId.asStateFlow()
    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly = _favoritesOnly.asStateFlow()
    val allEntries = repository.allEntries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val profiles = repository.allProfiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val entries = combine(allEntries, currentProfileId, favoritesOnly) { list, profile, favorites ->
        list.filter { (profile == null || it.profileId == profile) && (!favorites || it.isFavorite) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val rotationEntries = combine(allEntries, currentProfileId) { list, profile ->
        list.filter { (profile == null || it.profileId == profile) && it.rotationEnabled && it.isPasswordExpired() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val currentProfile = combine(profiles, currentProfileId) { list, id -> list.find { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setCurrentProfile(profileId: Int?) { _currentProfileId.value = profileId }
    fun toggleFavoritesOnly() { _favoritesOnly.value = !_favoritesOnly.value }
    fun findEntryById(entryId: String) = allEntries.value.find { it.id == entryId }
    suspend fun getEntryById(entryId: String) = repository.getById(entryId)
    private suspend fun entry(id: String) = repository.getById(id) ?: error("Запись не найдена")
    private fun perform(onResult: (PasswordOperationResult) -> Unit, block: suspend () -> String) {
        viewModelScope.launch {
            val result = try { PasswordOperationResult.Success(writes.withLock { block() })
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { PasswordOperationResult.Error(e.message ?: "Ошибка операции") }
            onResult(result)
        }
    }
    suspend fun saveEntry(updated: Entry, expected: Entry?) = writes.withLock {
        require(repository.getProfileById(updated.profileId) != null) { "Профиль не найден" }
        if (expected == null) {
            require(repository.getById(updated.id) == null) { "Запись уже сохранена" }
            repository.insert(updated)
        } else {
            require(repository.getById(expected.id) == expected) { "Запись изменилась. Откройте редактор заново" }
            require(updated.id == expected.id && updated.profileId == expected.profileId)
            repository.update(updated)
        }
    }
    fun insert(entry: Entry, onResult: (PasswordOperationResult) -> Unit = {}) = perform(onResult) {
        require(repository.getProfileById(entry.profileId) != null) { "Профиль не найден" }
        require(repository.getById(entry.id) == null) { "Запись уже существует" }
        repository.insert(entry); "Запись добавлена"
    }
    fun insertEntry(entry: Entry, onResult: (PasswordOperationResult) -> Unit) = insert(entry, onResult)
    fun updateEntry(entry: Entry, onResult: (PasswordOperationResult) -> Unit) = perform(onResult) {
        require(this.entry(entry.id).profileId == entry.profileId) { "Запись другого профиля" }
        repository.update(entry); "Запись обновлена"
    }
    fun toggleFavorite(entry: Entry, onResult: (PasswordOperationResult) -> Unit = {}) = perform(onResult) {
        val current = this.entry(entry.id)
        repository.update(current.copy(isFavorite = !current.isFavorite)); "Готово"
    }
    fun deleteEntry(entryId: String, expectedProfileId: Int, onResult: (PasswordOperationResult) -> Unit) =
        deleteEntries(listOf(entryId), expectedProfileId, onResult)
    fun deleteEntries(entryIds: List<String>, expectedProfileId: Int,
                      onResult: (PasswordOperationResult) -> Unit) = perform(onResult) {
        val items = entryIds.distinct().map { entry(it) }
        require(items.all { it.profileId == expectedProfileId }) { "Выбрана запись другого профиля" }
        repository.deleteMany(items); "Удалено записей: ${items.size}"
    }
    fun deleteAllEntriesInProfile(profileId: Int, onResult: (PasswordOperationResult) -> Unit) = perform(onResult) {
        val items = repository.getByProfileId(profileId)
        repository.deleteMany(items); "Удалено записей: ${items.size}"
    }
    fun deleteEntry(entryId: String, onResult: (PasswordOperationResult) -> Unit = {}) = perform(onResult) {
        repository.delete(entry(entryId)); "Запись удалена"
    }
    fun addToPasswordHistory(entry: Entry, oldEncryptedPassword: String?, type: String,
        relatedService: String? = null, onResult: (PasswordOperationResult) -> Unit = {}) = perform(onResult) {
        val current = this.entry(entry.id)
        val old = requireNotNull(oldEncryptedPassword) { "Старый пароль отсутствует" }
        val plain = CryptoUtils.decrypt(old)
        repository.update(current.addToPasswordHistory(plain, type,
            PasswordValidator.buildPasswordFingerprint(plain, appContext), relatedService)); "История обновлена"
    }
    fun replacePassword(entry: Entry, newPassword: String, generationType: String,
        onResult: (PasswordOperationResult) -> Unit) = replacePassword(entry.id, newPassword, generationType, onResult)
    fun replacePassword(entryId: String, newPassword: String, generationType: String,
        onResult: (PasswordOperationResult) -> Unit) =
        replacePassword(entryId, newPassword, null, generationType, null, null, onResult)
    fun replacePassword(entry: Entry, newPassword: String, newHint: String?, newGenerationType: String,
        newMnemonicPhraseHint: String?, newMnemonicOptionsJson: String?, onResult: (PasswordOperationResult) -> Unit) =
        replacePassword(entry.id, newPassword, newHint, newGenerationType, newMnemonicPhraseHint, newMnemonicOptionsJson, onResult)
    fun replacePassword(entryId: String, newPassword: String, newHint: String?, newGenerationType: String,
        newMnemonicPhraseHint: String?, newMnemonicOptionsJson: String?,
        onResult: (PasswordOperationResult) -> Unit) = perform(onResult) {
        val current = entry(entryId)
        repository.update(EntryChanges.replace(current, newPassword, newGenerationType,
            appContext, newHint, newMnemonicPhraseHint, newMnemonicOptionsJson)); "Пароль обновлён"
    }
    fun replacePassword(entryId: String, newHint: String?, newGenerationType: String,
        newMnemonicPhraseHint: String?, newMnemonicOptionsJson: String?,
        onResult: (PasswordOperationResult) -> Unit) = perform(onResult) {
        val current = entry(entryId)
        repository.update(current.copy(textHint = newHint, generationType = newGenerationType,
            mnemonicPhraseHint = newMnemonicPhraseHint, mnemonicOptionsJson = newMnemonicOptionsJson))
        "Параметры обновлены"
    }
    fun applyManagedShuffle(assignments: Map<String, String?>,
        onResult: (PasswordOperationResult) -> Unit) = perform(onResult) {
        require(assignments.size >= 2) { "Выберите минимум две записи" }
        require(assignments.values.none { it == null } && assignments.values.toSet() == assignments.keys) {
            "Каждая выбранная запись должна иметь одного уникального донора"
        }
        require(assignments.none { it.key == it.value }) { "Нельзя выбрать себя донором" }
        val source = assignments.keys.associateWith { entry(it) }
        require(source.values.map { it.profileId }.distinct().size == 1) { "Выбраны разные профили" }
        val updates = assignments.map { (targetId, sourceId) ->
            val donor = source.getValue(requireNotNull(sourceId))
            EntryChanges.replace(source.getValue(targetId), donor.password, donor.generationType,
                appContext, donor.textHint, donor.mnemonicPhraseHint, donor.mnemonicOptionsJson, donor.service)
        }
        repository.updateMany(updates); "Перемешано записей: ${updates.size}"
    }
    fun bulkReplacePasswords(replacements: List<BulkPasswordReplacement>,
        onResult: (PasswordOperationResult) -> Unit) = perform(onResult) {
        require(replacements.map { it.entryId }.distinct().size == replacements.size) { "Повтор записи" }
        val originals = replacements.map { entry(it.entryId) }
        require(originals.map { it.profileId }.distinct().size <= 1) { "Выбраны разные профили" }
        val updates = replacements.zip(originals).map { (replacement, current) ->
            EntryChanges.replace(current, replacement.newPassword, replacement.generationType,
                appContext, replacement.textHint, replacement.mnemonicPhraseHint, replacement.mnemonicOptionsJson)
        }
        repository.updateMany(updates); "Пароли обновлены"
    }
    fun addProfile(profile: Profile, onResult: (PasswordOperationResult) -> Unit) = perform(onResult) {
        repository.insertProfile(profile); "Профиль добавлен"
    }
    fun updateProfile(profile: Profile, onResult: (PasswordOperationResult) -> Unit = {}) = perform(onResult) {
        repository.updateProfile(profile); "Профиль обновлён"
    }
    fun deleteProfile(profileId: Int, onResult: (PasswordOperationResult) -> Unit) = perform(onResult) {
        require(repository.getByProfileId(profileId).isEmpty()) { "Сначала удалите пароли профиля" }
        repository.deleteProfile(profileId); "Профиль удалён"
    }
    fun setProfilePin(profileId: Int, pin: String, onResult: (PasswordOperationResult) -> Unit) = perform(onResult) {
        val profile = repository.getProfileById(profileId) ?: error("Профиль не найден")
        val salt = ProfilePasswordHasher.generateSalt()
        repository.updateProfile(profile.copy(passwordHash = ProfilePasswordHasher.hash(pin, salt), passwordSalt = salt))
        "PIN установлен"
    }
    fun verifyProfilePin(profileId: Int, pin: String): Boolean = profiles.value.find { it.id == profileId }?.let {
        ProfilePasswordHasher.verify(pin, it.passwordHash, it.passwordSalt)
    } ?: false
    suspend fun exportAllProfiles() = BackupManager.exportAllProfiles(repository, appContext)
    suspend fun importBackup(backupData: BackupData, mode: ImportMode, newPin: String?) =
        writes.withLock { BackupManager.importBackup(repository, backupData, mode, newPin, appContext) }
}

data class BulkPasswordReplacement(val entryId: String, val newPassword: String, val generationType: String,
    val textHint: String? = null, val mnemonicPhraseHint: String? = null, val mnemonicOptionsJson: String? = null)
data class PasswordShuffleAssignment(val entryId: String, val newPassword: String, val generationType: String,
    val targetProfileId: Int? = null)
