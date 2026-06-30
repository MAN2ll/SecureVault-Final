package com.securevault.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemeManager {
    private val THEME_KEY = stringPreferencesKey("app_theme")
    enum class AppTheme { SYSTEM, LIGHT, DARK }

    fun getThemeFlow(context: Context): Flow<AppTheme> {
        return context.dataStore.data.map { prefs ->
            val name = prefs[THEME_KEY] ?: AppTheme.SYSTEM.name
            AppTheme.valueOf(name)
        }
    }

    suspend fun saveTheme(context: Context, theme: AppTheme) {
        context.dataStore.edit { it[THEME_KEY] = theme.name }
    }
}
