package com.cloudflare.manager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val FOLLOW_SYSTEM_THEME = booleanPreferencesKey("follow_system_theme")
    }

    val followSystemTheme: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FOLLOW_SYSTEM_THEME] != false
    }

    val darkMode: Flow<Boolean?> = dataStore.data.map { prefs ->
        prefs[DARK_MODE]
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[DARK_MODE] = enabled
        }
    }

    suspend fun setFollowSystemTheme(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[FOLLOW_SYSTEM_THEME] = enabled
        }
    }
}
