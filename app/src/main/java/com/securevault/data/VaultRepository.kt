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

    suspend fun insert(entry: Entry) = dao.insert(entry)
    suspend fun update(entry: Entry) = dao.update(entry)
    suspend fun delete(entry: Entry) = dao.delete(entry)
    suspend fun deleteById(id: String) = dao.deleteById(id)
    suspend fun getById(id: String): Entry? = dao.getById(id)
    
    // Поиск
    fun searchByService(query: String): Flow<List<Entry>> = dao.searchByService(query)
    
    //  Профили
    fun getByProfile(profile: Profile): Flow<List<Entry>> = dao.getByProfile(profile)
    
    //  Просроченные
    fun getExpiredPasswords(): Flow<List<Entry>> = dao.getExpiredPasswords()

    //  Создание с шифрованием
    suspend fun createEntry(
        service: String,
        username: String,
        password: String,
        category: String = "general",
        profile: Profile = Profile.PERSONAL,
        notes: String = "",
        changeIntervalDays: Int = 90
    ) {
        val entry = Entry.create(service, username, password, category, profile, notes, changeIntervalDays)
        dao.insert(entry)
    }
    
    //  Защита от подбора
    suspend fun incrementFailedAttempts(entryId: String) {
        val entry = dao.getById(entryId) ?: return
        val newAttempts = entry.failedAttempts + 1
        // Прогрессивная блокировка
        val lockUntil = when {
            newAttempts >= 10 -> Long.MAX_VALUE  //  Самоуничтожение (обрабатывается в UI)
            newAttempts >= 7  -> System.currentTimeMillis() + 5 * 60_000  // 5 минут
            newAttempts >= 5  -> System.currentTimeMillis() + 30_000  // 30 секунд
            newAttempts >= 3  -> System.currentTimeMillis() + 10_000  // 10 секунд
            else -> 0L
        }
        dao.update(entry.copy(
            failedAttempts = newAttempts,
            lockedUntil = lockUntil
        ))
    }
    
    suspend fun resetFailedAttempts(entryId: String) {
        val entry = dao.getById(entryId) ?: return
        dao.update(entry.copy(failedAttempts = 0, lockedUntil = 0L))
    }
}
