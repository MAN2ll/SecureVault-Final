package com.securevault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Profile::class, Entry::class],
    version = 10, 
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE entries ADD COLUMN password_access_mode TEXT NOT NULL DEFAULT 'INHERIT'")
                database.execSQL("ALTER TABLE profiles ADD COLUMN password_access_mode TEXT NOT NULL DEFAULT 'PIN_REQUIRED'")
            }
        }

        //  Миграция 9 → 10 для profileAccessMode
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE profiles ADD COLUMN profile_access_mode TEXT NOT NULL DEFAULT 'PIN_REQUIRED'")
            }
        }

        fun getDatabase(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "securevault_database"
                )
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
