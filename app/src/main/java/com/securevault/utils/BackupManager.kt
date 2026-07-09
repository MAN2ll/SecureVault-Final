package com.securevault.utils

import android.content.Context
import android.util.Base64
import com.securevault.data.*
import com.securevault.security.ProfilePasswordHasher
import kotlinx.coroutines.flow.first
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

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes
    }

    suspend fun exportAllProfiles(repository: VaultRepository): BackupData {
        val profiles = repository.allProfiles.first()
        val backupProfiles = profiles.map { profile ->
            val entries = repository.getByProfileId(profile.id)
            val backupEntries = entries.map { entry ->
                val plainPassword = try {
                    entry.password
                } catch (e: Exception) {
                    throw Exception("Не удалось расшифровать пароль '${entry.service}': ${e.message}")
                }
                BackupEntry(
                    service = entry.service,
                    username = entry.username,
                    password = plainPassword,
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

    // Добавлен параметр Context для fingerprint
    suspend fun importBackup(
        repository: VaultRepository,
        backupData: BackupData,
        mode: ImportMode,
        newPin: String,
        context: Context 
    ): ImportResult {
        val profileMapping = mutableMapOf<Int, Int>()
        var importedProfiles = 0
        var importedEntries = 0
        val errors = mutableListOf<String>()

        val pinSalt = ProfilePasswordHasher.generateSalt()
        val pinHash = ProfilePasswordHasher.hash(newPin, pinSalt)

        for (backupProfile in backupData.profiles) {
            try {
                val existingProfile = repository.getProfileByName(backupProfile.name)

                val newProfileId = when (mode) {
                    ImportMode.ADD_AS_NEW -> {
                        val uniqueName = generateUniqueProfileName(repository, backupProfile.name)
                        val newProfile = Profile(
                            name = uniqueName,
                            passwordHash = pinHash,
                            passwordSalt = pinSalt
                        )
                        repository.insertProfile(newProfile).toInt()
                    }
                    ImportMode.MERGE_IF_EXISTS -> {
                        if (existingProfile != null) {
                            existingProfile.id
                        } else {
                            val newProfile = Profile(
                                name = backupProfile.name,
                                passwordHash = pinHash,
                                passwordSalt = pinSalt
                            )
                            repository.insertProfile(newProfile).toInt()
                        }
                    }
                    ImportMode.SKIP_IF_EXISTS -> {
                        if (existingProfile != null) {
                            continue
                        } else {
                            val newProfile = Profile(
                                name = backupProfile.name,
                                passwordHash = pinHash,
                                passwordSalt = pinSalt
                            )
                            repository.insertProfile(newProfile).toInt()
                        }
                    }
                }

                profileMapping[backupProfile.oldProfileId] = newProfileId
                importedProfiles++

                for (backupEntry in backupProfile.entries) {
                    try {
                        //  Используем актуальный fingerprint с Context
                        val newEntry = Entry.create(
                            service = backupEntry.service,
                            username = backupEntry.username,
                            password = backupEntry.password,
                            profileId = newProfileId,
                            passwordFingerprint = PasswordValidator.buildPasswordFingerprint(backupEntry.password, context),
                            url = backupEntry.url,
                            notes = backupEntry.notes,
                            textHint = backupEntry.textHint,
                            isFavorite = backupEntry.isFavorite,
                            rotationEnabled = backupEntry.rotationEnabled,
                            rotationPeriodMonths = backupEntry.rotationPeriodMonths,
                            generationType = backupEntry.generationType,
                            mnemonicPhraseHint = backupEntry.mnemonicPhraseHint,
                            mnemonicOptionsJson = backupEntry.mnemonicOptionsJson
                        ).copy(
                            nextRotationDate = backupEntry.nextRotationDate,
                            createdAt = backupEntry.createdAt,
                            lastChanged = backupEntry.lastChanged,
                            passwordHistoryJson = backupEntry.passwordHistoryJson
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
    ADD_AS_NEW,
    MERGE_IF_EXISTS,
    SKIP_IF_EXISTS
}

data class ImportResult(
    val success: Boolean,
    val importedProfiles: Int,
    val importedEntries: Int,
    val profileMapping: Map<Int, Int>,
    val errors: List<String>
)
