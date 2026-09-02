package com.securevault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Entry::class, Profile::class],
    version = 11,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    
    //  ИЗМЕНЕНО: используем EntryDao и ProfileDao вместо несуществующего VaultDao
    abstract fun entryDao(): EntryDao
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        // БЛОК 7: Миграция с версии 10 на 11 (добавление колонки tags_csv)
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE entries ADD COLUMN tags_csv TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "vault_database"
                )
                .addMigrations(
                    MIGRATION_10_11 
                )
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
