package org.school.neurotranslate.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.school.neurotranslate.settings.normalizeMaxOutputLength

private const val DEFAULT_THEME_MODE = "System"
private const val DEFAULT_MAX_OUTPUT_LENGTH = 256

data class AppSettings(
    val themeModeName: String = DEFAULT_THEME_MODE,
    val maxOutputLength: Int = DEFAULT_MAX_OUTPUT_LENGTH
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = dataStore.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }
        .map { preferences ->
            AppSettings(
                themeModeName = preferences[THEME_MODE_KEY] ?: DEFAULT_THEME_MODE,
                maxOutputLength = normalizeMaxOutputLength(
                    (preferences[MAX_OUTPUT_LENGTH_KEY] ?: DEFAULT_MAX_OUTPUT_LENGTH).toFloat()
                )
            )
        }

    suspend fun setThemeMode(themeModeName: String) {
        dataStore.edit { it[THEME_MODE_KEY] = themeModeName }
    }

    suspend fun setMaxOutputLength(maxOutputLength: Int) {
        dataStore.edit {
            it[MAX_OUTPUT_LENGTH_KEY] = normalizeMaxOutputLength(maxOutputLength.toFloat())
        }
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val MAX_OUTPUT_LENGTH_KEY = intPreferencesKey("max_output_length")

        fun from(context: Context): SettingsRepository = SettingsRepository(context.settingsDataStore)
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")
