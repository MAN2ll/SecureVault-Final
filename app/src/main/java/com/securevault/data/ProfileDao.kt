package com.securevault.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name")
    fun getAllProfiles(): Flow<List<Profile>>

    @Insert
    suspend fun insert(profile: Profile): Long

    @Update
    suspend fun update(profile: Profile)

    @Delete
    suspend fun delete(profile: Profile)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Int): Profile?

    @Query("SELECT * FROM profiles WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Profile?
}
