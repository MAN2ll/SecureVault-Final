package com.securevault.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    private val dao: EntryDao
) {
    val allEntries: Flow<List<Entry>> = dao.getAll()
    val favoriteEntries: Flow<List<Entry>> = dao.getFavorites()

    // НОВОЕ: Поток записей по профилю
    fun getEntriesByProfile(profile: Profile): Flow<List<Entry>> = dao.getByProfile(profile)

    suspend fun insert(entry: Entry) = dao.insert(entry)
    suspend fun update(entry: Entry) = dao.update(entry)
    suspend fun delete(entry: Entry) = dao.delete(entry)
    suspend fun deleteById(id: String) = dao.deleteById(id)
    suspend fun getById(id: String): Entry? = dao.getById(id)
    
    suspend fun createEntry(
        service: String, username: String, password: String,
        category: String = "general", profile: Profile = Profile.PERSONAL,
        notes: String = "", changeIntervalDays: Int = 90
    ) {
        val entry = Entry.create(service, username, password, category, profile, notes, changeIntervalDays)
        dao.insert(entry)
    }
}
