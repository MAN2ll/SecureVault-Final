package com.securevault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Entry::class, Profile::class], // Убедись, что Profile::class есть в твоем оригинальном списке
    version = 11, //  Увеличена версия базы
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        //  БЛОК 7: Миграция с версии 10 на 11 (добавление tags_csv)
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE entries ADD COLUMN tags_csv TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        // Если у тебя были другие миграции (например, 8_9, 9_10), оставь их здесь:
        // val MIGRATION_8_9 = object : Migration(8, 9) { ... }
        // val MIGRATION_9_10 = object : Migration(9, 10) { ... }

        fun getDatabase(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "vault_database"
                )
                .addMigrations(
                    // Добавь сюда свои старые миграции, если они были
                    MIGRATION_10_11 //  Подключаем новую миграцию
                )
                //  ВАЖНО: fallbackToDestructiveMigration должен быть ЗАКОММЕНТИРОВАН или удален, 
                // чтобы миграция сработала, а не удалила базу!
                // .fallbackToDestructiveMigration() 
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
