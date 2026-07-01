package com.securevault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ✅ Миграция с версии 6 на 7: добавление поля generation_type
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Добавляем колонку generation_type со значением по умолчанию 'random'
        database.execSQL("ALTER TABLE entries ADD COLUMN generation_type TEXT NOT NULL DEFAULT 'random'")
    }
}

@Database(
    entities = [Entry::class, Profile::class],
    version = 7,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getDatabase(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "vault_db"
                )
                // ✅ ДОБАВЛЕНА НОРМАЛЬНАЯ МИГРАЦИЯ ВМЕСТО DESTRUCTIVE
                .addMigrations(MIGRATION_6_7)
                //  УБРАНО: fallbackToDestructiveMigration()
                // Для приложения с паролями недопустимо удалять данные при миграции
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context) = VaultDatabase.getDatabase(context)

    @Provides
    @Singleton
    fun provideEntryDao(db: VaultDatabase) = db.entryDao()

    @Provides
    @Singleton
    fun provideProfileDao(db: VaultDatabase) = db.profileDao()
}
