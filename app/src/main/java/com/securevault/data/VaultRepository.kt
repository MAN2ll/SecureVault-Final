package com.securevault.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val profileDao: ProfileDao
) {
    // ===== Entries =====
    
    val allEntries: Flow<List<Entry>> = entryDao.getAllEntries()

    suspend fun insert(entry: Entry) {
        entryDao.insert(entry)
    }

    suspend fun update(entry: Entry) {
        entryDao.update(entry)
    }

    suspend fun delete(entry: Entry) {
        entryDao.delete(entry)
    }

    suspend fun deleteAll() {
        entryDao.deleteAll()
    }

    suspend fun getById(entryId: String): Entry? {
        return entryDao.getById(entryId)
    }

    fun getByIdBlocking(entryId: String): Entry? {
        return runBlocking {
            entryDao.getById(entryId)
        }
    }

    // ✅ НОВЫЙ МЕТОД для PasswordReminderReceiver
    fun getAllEntriesSync(): List<Entry> {
        return runBlocking {
            entryDao.getAllEntries().first()
        }
    }

    suspend fun getByProfileId(profileId: Int): List<Entry> {
        return entryDao.getByProfileId(profileId)
    }

    fun getEntriesByProfile(profileId: Int): Flow<List<Entry>> {
        return entryDao.getEntriesByProfile(profileId)
    }

    // ===== Profiles =====
    
    val allProfiles: Flow<List<Profile>> = profileDao.getAllProfiles()

    suspend fun insertProfile(profile: Profile): Long {
        return profileDao.insert(profile)
    }

    suspend fun updateProfile(profile: Profile) {
        profileDao.update(profile)
    }

    suspend fun deleteProfile(profile: Profile) {
        profileDao.delete(profile)
    }

    // ✅ ПЕРЕГРУЗКА: удаление по ID (для ProfileViewModel)
    suspend fun deleteProfile(id: Int) {
        profileDao.deleteById(id)
    }

    suspend fun getProfileById(profileId: Int): Profile? {
        return profileDao.getById(profileId)
    }

    suspend fun getProfileByName(name: String): Profile? {
        return profileDao.getByName(name)
    }

    // ===== Связанные операции =====

    suspend fun deleteEntriesByProfileId(profileId: Int) {
        entryDao.deleteByProfileId(profileId)
    }

    suspend fun getEntriesWithRotation(): List<Entry> {
        return entryDao.getEntriesWithRotation()
    }

    suspend fun getExpiredEntries(): List<Entry> {
        val now = System.currentTimeMillis()
        return entryDao.getExpiredEntries(now)
    }

    suspend fun getEntriesExpiringSoon(daysThreshold: Int): List<Entry> {
        val now = System.currentTimeMillis()
        val threshold = now + (daysThreshold * 24L * 60 * 60 * 1000)
        return entryDao.getEntriesExpiringSoon(now, threshold)
    }
}
