package com.securevault.utils

import android.content.Context
import android.util.Base64
import com.securevault.data.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupManager {
    
    private const val KEY_LENGTH = 256
    private const val ITERATIONS = 200000
    private const val GCM_TAG_LENGTH = 128
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    
    // Шифрование backup-данных
    fun encryptBackup(backupData: BackupData, password: String): EncryptedBackup {
        val salt = generateRandomBytes(SALT_LENGTH)
        val iv = generateRandomBytes(IV_LENGTH)
        
        val key = deriveKey(password, salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        
        val plaintext = backupData.toJson().toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintext)
        
        return EncryptedBackup(
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }
    
    // Дешифрование backup-данных
    fun decryptBackup(encryptedBackup: EncryptedBackup, password: String): BackupData {
        val salt = Base64.decode(encryptedBackup.salt, Base64.NO_WRAP)
        val iv = Base64.decode(encryptedBackup.iv, Base64.NO_WRAP)
        val ciphertext = Base64.decode(encryptedBackup.ciphertext, Base64.NO_WRAP)
        
        val key = deriveKey(password, salt, encryptedBackup.iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        
        val plaintext = cipher.doFinal(ciphertext)
        val jsonString = String(plaintext, Charsets.UTF_8)
        
        return BackupData.fromJson(jsonString)
    }
    
    // Генерация ключа из пароля через PBKDF2
    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
    
    // Генерация случайных байтов
    private fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes
    }
    
    // Экспорт всех профилей и записей
    fun exportAllProfiles(repository: VaultRepository): BackupData {
        val profiles = repository.allProfiles.value
        val backupProfiles = profiles.map { profile ->
            val entries = repository.getByProfileId(profile.id)
            val backupEntries = entries.map { entry ->
                BackupEntry(
                    service = entry.service,
                    username = entry.username,
                    encryptedPassword = entry.encryptedPassword,
                    url = entry.url,
                    notes = entry.notes,
                    textHint = entry.textHint,
                    generationType = entry.generationType,
                    mnemonicPhraseHint = entry.mnemonicPhraseHint,
                    mnemonicOptionsJson = entry.mnemonicOptionsJson,
                    rotationEnabled = entry.rotationEnabled,
                    rotationPeriodMonths = entry.rotationPeriodMonths,
                    nextRotationDate = entry.nextRotationDate,
                    isFavorite = entry.isFavorite,
                    createdAt = entry.createdAt,
                    lastChanged = entry.lastChanged,
                    passwordHistoryJson = entry.passwordHistoryJson,
                    passwordFingerprint = entry.passwordFingerprint
                )
            }
            BackupProfile(profile.id, profile.name, backupEntries)
        }
        return BackupData(profiles = backupProfiles)
    }
    
    // Импорт backup с маппингом профилей
    suspend fun importBackup(
        repository: VaultRepository,
        backupData: BackupData,
        mode: ImportMode
    ): ImportResult {
        val profileMapping = mutableMapOf<Int, Int>() // oldProfileId -> newProfileId
        var importedProfiles = 0
        var importedEntries = 0
        val errors = mutableListOf<String>()
        
        for (backupProfile in backupData.profiles) {
            try {
                // Проверяем, существует ли профиль с таким именем
                val existingProfile = repository.getProfileByName(backupProfile.name)
                
                val newProfileId = when (mode) {
                    ImportMode.ADD_AS_NEW -> {
                        // Создаём новый профиль с уникальным именем
                        val uniqueName = generateUniqueProfileName(repository, backupProfile.name)
                        val newProfile = Profile(name = uniqueName, passwordHash = "", passwordSalt = "")
                        repository.insertProfile(newProfile).toInt()
                    }
                    ImportMode.MERGE_IF_EXISTS -> {
                        if (existingProfile != null) {
                            // Используем существующий профиль
                            existingProfile.id
                        } else {
                            // Создаём новый профиль
                            val newProfile = Profile(name = backupProfile.name, passwordHash = "", passwordSalt = "")
                            repository.insertProfile(newProfile).toInt()
                        }
                    }
                    ImportMode.SKIP_IF_EXISTS -> {
                        if (existingProfile != null) {
                            // Пропускаем этот профиль
                            continue
                        } else {
                            val newProfile = Profile(name = backupProfile.name, passwordHash = "", passwordSalt = "")
                            repository.insertProfile(newProfile).toInt()
                        }
                    }
                }
                
                profileMapping[backupProfile.oldProfileId] = newProfileId
                importedProfiles++
                
                // Импортируем записи
                for (backupEntry in backupProfile.entries) {
                    try {
                        val newEntry = Entry.createWithNewId(
                            service = backupEntry.service,
                            username = backupEntry.username,
                            encryptedPassword = backupEntry.encryptedPassword,
                            profileId = newProfileId,
                            url = backupEntry.url,
                            notes = backupEntry.notes,
                            textHint = backupEntry.textHint,
                            isFavorite = backupEntry.isFavorite,
                            rotationEnabled = backupEntry.rotationEnabled,
                            rotationPeriodMonths = backupEntry.rotationPeriodMonths,
                            nextRotationDate = backupEntry.nextRotationDate,
                            createdAt = backupEntry.createdAt,
                            lastChanged = backupEntry.lastChanged,
                            passwordHistoryJson = backupEntry.passwordHistoryJson,
                            generationType = backupEntry.generationType,
                            passwordFingerprint = backupEntry.passwordFingerprint,
                            mnemonicPhraseHint = backupEntry.mnemonicPhraseHint,
                            mnemonicOptionsJson = backupEntry.mnemonicOptionsJson
                        )
                        repository.insert(newEntry)
                        importedEntries++
                    } catch (e: Exception) {
                        errors.add("Ошибка импорта записи '${backupEntry.service}': ${e.message}")
                    }
                }
            } catch (e: Exception) {
                errors.add("Ошибка импорта профиля '${backupProfile.name}': ${e.message}")
            }
        }
        
        return ImportResult(
            success = errors.isEmpty(),
            importedProfiles = importedProfiles,
            importedEntries = importedEntries,
            profileMapping = profileMapping,
            errors = errors
        )
    }
    
    // Генерация уникального имени профиля
    private suspend fun generateUniqueProfileName(repository: VaultRepository, baseName: String): String {
        var name = baseName
        var counter = 1
        while (repository.getProfileByName(name) != null) {
            name = "$baseName ($counter)"
            counter++
        }
        return name
    }
}

enum class ImportMode {
    ADD_AS_NEW,      // Добавить как новые профили
    MERGE_IF_EXISTS, // Объединить с существующими
    SKIP_IF_EXISTS   // Пропустить, если существует
}

data class ImportResult(
    val success: Boolean,
    val importedProfiles: Int,
    val importedEntries: Int,
    val profileMapping: Map<Int, Int>,
    val errors: List<String>
)
