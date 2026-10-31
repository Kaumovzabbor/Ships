package com.friends.ships.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.shipsDataStore by preferencesDataStore(name = "ships_settings")

data class AppSettings(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val theme: String = "system",
    val language: String = "uz"
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
    }

    val settings: Flow<AppSettings> = context.shipsDataStore.data.map { p ->
        AppSettings(
            soundEnabled = p[Keys.SOUND] ?: true,
            vibrationEnabled = p[Keys.VIBRATION] ?: true,
            theme = p[Keys.THEME] ?: "system",
            language = p[Keys.LANGUAGE] ?: "uz"
        )
    }

    suspend fun setSound(enabled: Boolean) = context.shipsDataStore.edit { it[Keys.SOUND] = enabled }
    suspend fun setVibration(enabled: Boolean) = context.shipsDataStore.edit { it[Keys.VIBRATION] = enabled }
    suspend fun setTheme(theme: String) = context.shipsDataStore.edit { it[Keys.THEME] = theme }
    suspend fun setLanguage(language: String) = context.shipsDataStore.edit { it[Keys.LANGUAGE] = language }
}
