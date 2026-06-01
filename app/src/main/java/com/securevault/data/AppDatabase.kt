package com.securevault.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.securevault.utils.Converters

@Database(
    entities = [Entry::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun entryDao(): EntryDao
    
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(builder: Builder<AppDatabase>): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = builder
                    // ✅ Для разработки: сброс БД при изменении схемы
                    // В продакшене нужно писать миграции
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
