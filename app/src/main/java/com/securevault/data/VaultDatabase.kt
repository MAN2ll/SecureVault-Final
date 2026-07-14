package com.securevault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Profile::class, Entry::class],
    version = 9, //  Поднята версия до 9
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        //  Миграция для новых полей
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE entries ADD COLUMN password_access_mode TEXT NOT NULL DEFAULT 'INHERIT'")
                database.execSQL("ALTER TABLE profiles ADD COLUMN password_access_mode TEXT NOT NULL DEFAULT 'PIN_REQUIRED'")
            }
        }

        fun getDatabase(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "securevault_database"
                )
                // Добавьте сюда ваши старые миграции, если они есть (MIGRATION_6_7, MIGRATION_7_8)
                .addMigrations(MIGRATION_8_9)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
