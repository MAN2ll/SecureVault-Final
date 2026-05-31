package com.securevault.security

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.securevault.data.VaultDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataWiper @Inject constructor(
    @ApplicationContext private val context: Context  // Добавлена аннотация
) {

    companion object {
        private const val WIPE_CONFIRMATION_KEY = "wipe_confirmed"
    }

    suspend fun wipeAllData(): WipeResult {
        return try {
            wipeSharedPreferences()
            wipeRoomDatabase()
            wipeFiles()
            WipeResult.Success
        } catch (e: Exception) {
            WipeResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun wipeSharedPreferences() {
        val prefs: SharedPreferences = context.getSharedPreferences(
            context.packageName + "_preferences",
            Context.MODE_PRIVATE
        )
        prefs.edit().clear().apply()
        
        try {
            context.deleteSharedPreferences("brute_force_prefs")
            context.deleteSharedPreferences("session_prefs")
        } catch (e: Exception) {
        }
    }

    private suspend fun wipeRoomDatabase() {
        try {
            val db = Room.databaseBuilder(
                context,
                VaultDatabase::class.java,
                "vault_db"
            ).build()
            db.clearAllTables()
            db.close()
            
            context.deleteDatabase("vault_db")
        } catch (e: Exception) {
        }
    }

    private fun wipeFiles() {
        try {
            context.filesDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".vault") || file.name.endsWith(".bak")) {
                    file.delete()
                }
            }
            
            context.cacheDir.listFiles()?.forEach { file ->
                file.delete()
            }
        } catch (e: Exception) {
        }
    }

    fun markWipeConfirmed() {
        val prefs = context.getSharedPreferences(
            context.packageName + "_preferences",
            Context.MODE_PRIVATE
        )
        prefs.edit().putBoolean(WIPE_CONFIRMATION_KEY, true).apply()
    }

    fun isWipeConfirmed(): Boolean {
        val prefs = context.getSharedPreferences(
            context.packageName + "_preferences",
            Context.MODE_PRIVATE
        )
        return prefs.getBoolean(WIPE_CONFIRMATION_KEY, false)
    }

    sealed class WipeResult {
        object Success : WipeResult()
        data class Error(val message: String) : WipeResult()
    }
}
