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
        
        fun getDatabase(androidxRoomDatabaseBuilder: androidx.room.RoomDatabase.Builder<AppDatabase>): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidxRoomDatabaseBuilder
                    .fallbackToDestructiveMigration() // ✅ Сброс базы при изменении схемы (для разработки)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
