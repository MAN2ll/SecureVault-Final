package com.securevault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(entities = [Entry::class, Profile::class], version = 7, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile private var INSTANCE: VaultDatabase? = null

        fun getDatabase(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, VaultDatabase::class.java, "vault_db"
                )
                // TODO: CRITICAL! Для релизной версии менеджера паролей НЕДОПУСТИМО использовать
                // fallbackToDestructiveMigration(). При изменении схемы БД все пароли пользователей
                // будут безвозвратно удалены. Необходимо реализовать нормальные Migration с сохранением
                // данных пользователей. Например:
                // .addMigrations(MIGRATION_6_7, MIGRATION_7_8, ...)
                // где каждая миграция добавляет новые колонки или изменяет схему без потери данных.
                .fallbackToDestructiveMigration()
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
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context) = VaultDatabase.getDatabase(context)

    @Provides @Singleton
    fun provideEntryDao(db: VaultDatabase) = db.entryDao()

    @Provides @Singleton
    fun provideProfileDao(db: VaultDatabase) = db.profileDao()
}
