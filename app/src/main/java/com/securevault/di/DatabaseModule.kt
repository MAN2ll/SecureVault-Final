package com.securevault.di

import android.content.Context
import androidx.room.Room
import com.securevault.data.EntryDao
import com.securevault.data.ProfileDao
import com.securevault.data.VaultDatabase
import com.securevault.data.VaultRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VaultDatabase {
        return VaultDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideEntryDao(database: VaultDatabase): EntryDao {
        return database.entryDao()
    }

    @Provides
    @Singleton
    fun provideProfileDao(database: VaultDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    @Singleton
    fun provideVaultRepository(
        entryDao: EntryDao,
        profileDao: ProfileDao,
        @ApplicationContext context: Context
    ): VaultRepository {
        return VaultRepository(entryDao, profileDao, context) // Адаптируй под конструктор твоего Repository
    }
}
