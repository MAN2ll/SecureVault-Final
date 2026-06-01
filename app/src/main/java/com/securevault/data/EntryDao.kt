package com.securevault.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    
    @Query("SELECT * FROM entries ORDER BY last_changed DESC")
    fun getAll(): Flow<List<Entry>>
    
    @Query("SELECT * FROM entries WHERE profile = :profile ORDER BY last_changed DESC")
    fun getByProfile(profile: Profile): Flow<List<Entry>>
    
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: String): Entry?
    
    @Query("SELECT * FROM entries WHERE service LIKE :query OR username LIKE :query ORDER BY last_changed DESC")
    fun search(query: String): Flow<List<Entry>>
    
    // ✅ Запрос для избранного (использует существующую колонку is_favorite)
    @Query("SELECT * FROM entries WHERE is_favorite = 1 ORDER BY last_changed DESC")
    fun getFavorites(): Flow<List<Entry>>
    
    // ✅ Запрос для ротации
    @Query("SELECT * FROM entries WHERE rotation_enabled = 1 AND next_rotation_date IS NOT NULL ORDER BY next_rotation_date ASC")
    fun getRotatable(): Flow<List<Entry>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: Entry): Long
    
    @Update
    suspend fun update(entry: Entry)
    
    @Delete
    suspend fun delete(entry: Entry)
    
    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: String)
}
