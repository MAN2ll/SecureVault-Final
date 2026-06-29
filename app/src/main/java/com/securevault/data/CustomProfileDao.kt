package com.securevault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomProfileDao {
    @Query("SELECT * FROM custom_profiles ORDER BY name")
    fun getAllProfiles(): Flow<List<CustomProfile>>

    @Insert
    suspend fun insert(profile: CustomProfile)

    @Query("DELETE FROM custom_profiles WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM custom_profiles WHERE id = :id")
    suspend fun getById(id: Int): CustomProfile?
}
