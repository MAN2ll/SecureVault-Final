package com.securevault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    //  УБРАЛИ Profile::class, так как именно он чаще всего вызывает MissingType, 
    // если не импортирован или не является Room-сущностью в этом файле.
    entities = [Entry::class], 
    version = 11, 
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    
    abstract fun vaultDao(): VaultDao

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
