package com.securevault.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val profileDao: ProfileDao
) {
    val allEntries: Flow<List<Entry>> = entryDao.getAllEntries()
    val allProfiles: Flow<List<Profile>> = profileDao.getAllProfiles()

    suspend fun insert(entry: Entry) = entryDao.insert(entry)
    suspend fun update(entry: Entry) = entryDao.update(entry)
    suspend fun delete(entry: Entry) = entryDao.delete(entry)
    suspend fun deleteAll() = entryDao.deleteAll()
    suspend fun getById(id: String): Entry? = entryDao.getById(id)
    
    suspend fun insertProfile(profile: Profile): Long = profileDao.insert(profile)
    suspend fun deleteProfile(id: Int) = profileDao.delete(id)
    suspend fun getProfileById(id: Int): Profile? = profileDao.getById(id)
}
