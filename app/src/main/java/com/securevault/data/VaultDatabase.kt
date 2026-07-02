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

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE entries ADD COLUMN generation_type TEXT NOT NULL DEFAULT 'random'")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE entries ADD COLUMN password_fingerprint TEXT")
        database.execSQL("ALTER TABLE entries ADD COLUMN mnemonic_phrase_hint TEXT")
        database.execSQL("ALTER TABLE entries ADD COLUMN mnemonic_options_json TEXT")
    }
}

@Database(
    entities = [Entry::class, Profile::class],
    version = 8,
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
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
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
