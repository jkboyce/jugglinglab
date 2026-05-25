//
// StoredPreferences.kt
//
// Stored preferences for the mobile application.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeSetting {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromInt(value: Int): ThemeSetting {
            return entries.getOrNull(value) ?: SYSTEM
        }
    }
}

@Suppress("PrivatePropertyName")
class StoredPreferencesRepository(private val dataStore: DataStore<Preferences>) {
    private val THEME_KEY = intPreferencesKey("theme_setting")
    private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")

    val themeSettingFlow: Flow<ThemeSetting> = dataStore.data.map { preferences ->
        val themeValue = preferences[THEME_KEY] ?: ThemeSetting.SYSTEM.ordinal
        ThemeSetting.fromInt(themeValue)
    }

    val onboardingCompletedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    suspend fun saveThemeSetting(setting: ThemeSetting) {
        dataStore.edit { preferences ->
            preferences[THEME_KEY] = setting.ordinal
        }
    }

    suspend fun saveOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }
}
