package com.victorfalcon.dose.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultSnoozeMinutes: Int = 10,
    val firstRun: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SNOOZE_MINUTES = intPreferencesKey("default_snooze_minutes")
        val FIRST_RUN = booleanPreferencesKey("first_run")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            theme = prefs[Keys.THEME]?.let(ThemeMode::valueOf) ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            defaultSnoozeMinutes = prefs[Keys.SNOOZE_MINUTES] ?: 10,
            firstRun = prefs[Keys.FIRST_RUN] ?: true,
        )
    }

    suspend fun setTheme(theme: ThemeMode) =
        edit { it[Keys.THEME] = theme.name }

    suspend fun setDynamicColor(enabled: Boolean) =
        edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setDefaultSnoozeMinutes(minutes: Int) =
        edit { it[Keys.SNOOZE_MINUTES] = minutes }

    suspend fun setFirstRunDone() =
        edit { it[Keys.FIRST_RUN] = false }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
