package com.securevault.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    // Исправлено: lastChanged вместо updatedAt
    @Query("SELECT * FROM entries ORDER BY lastChanged DESC")
    fun getAll(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE isFavorite = 1")
    fun getFavorites(): Flow<List<Entry>>
    
    //  Поиск по сервису
    @Query("SELECT * FROM entries WHERE service LIKE :query ORDER BY lastChanged DESC")
    fun searchByService(query: String): Flow<List<Entry>>
    
    //  Фильтрация по профилю
    @Query("SELECT * FROM entries WHERE profile = :profile ORDER BY lastChanged DESC")
    fun getByProfile(profile: Profile): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: String): Entry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: Entry)

    @Update
    suspend fun update(entry: Entry)

    @Delete
    suspend fun delete(entry: Entry)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: String)
    
    // Записи, у которых истёк срок смены пароля
    @Query("""
        SELECT * FROM entries 
        WHERE lastChanged + (changeIntervalDays * 24 * 60 * 60 * 1000) < :now
        ORDER BY lastChanged ASC
    """)
    fun getExpiredPasswords(now: Long = System.currentTimeMillis()): Flow<List<Entry>>
}
