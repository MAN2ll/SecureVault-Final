package com.securevault.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY service ASC")
    fun getAllEntries(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: String): Entry?

    @Query("SELECT * FROM entries WHERE profile_id = :profileId ORDER BY service ASC")
    fun getEntriesByProfile(profileId: Int): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE profile_id = :profileId ORDER BY service ASC")
    suspend fun getByProfileId(profileId: Int): List<Entry>

    @Query("SELECT * FROM entries WHERE rotation_enabled = 1")
    suspend fun getEntriesWithRotation(): List<Entry>

    @Query("SELECT * FROM entries WHERE rotation_enabled = 1 AND next_rotation_date <= :now")
    suspend fun getExpiredEntries(now: Long): List<Entry>

    @Query("SELECT * FROM entries WHERE rotation_enabled = 1 AND next_rotation_date > :now AND next_rotation_date <= :threshold")
    suspend fun getEntriesExpiringSoon(now: Long, threshold: Long): List<Entry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: Entry)

    @Update
    suspend fun update(entry: Entry)

    @Delete
    suspend fun delete(entry: Entry)

    @Query("DELETE FROM entries")
    suspend fun deleteAll()

    @Query("DELETE FROM entries WHERE profile_id = :profileId")
    suspend fun deleteByProfileId(profileId: Int)
}
