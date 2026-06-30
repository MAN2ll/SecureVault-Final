package com.securevault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name")
    fun getAllProfiles(): Flow<List<Profile>>

    @Insert
    suspend fun insert(profile: Profile): Long

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Int): Profile?
}
