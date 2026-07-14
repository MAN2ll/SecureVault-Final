package com.securevault.di

import android.content.Context
import com.securevault.data.EntryDao
import com.securevault.data.ProfileDao
import com.securevault.data.VaultDatabase
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

    // provideVaultRepository
    // Поскольку VaultRepository имеет @Inject constructor(entryDao, profileDao),
    // Hilt автоматически создаст его экземпляр, используя предоставленные выше DAO.
    // Это устраняет ошибку "Too many arguments" и "Duplicate binding".
}
