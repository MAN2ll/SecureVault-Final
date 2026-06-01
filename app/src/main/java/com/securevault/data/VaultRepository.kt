package com.securevault.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    private val dao: EntryDao
) {
    val allEntries: Flow<List<Entry>> = dao.getAll()
    
    fun getEntriesByProfile(profile: Profile): Flow<List<Entry>> = dao.getByProfile(profile)
    
    suspend fun getById(id: String): Entry? = dao.getById(id)
    
    fun search(query: String): Flow<List<Entry>> = dao.search("%$query%")
    
    fun getFavorites(): Flow<List<Entry>> = dao.getFavorites()
    
    fun getRotatable(): Flow<List<Entry>> = dao.getRotatable()
    
    suspend fun insert(entry: Entry) = dao.insert(entry)
    
    suspend fun update(entry: Entry) = dao.update(entry)
    
    suspend fun delete(entry: Entry) = dao.delete(entry)
    
    suspend fun deleteById(id: String) = dao.deleteById(id)
}
