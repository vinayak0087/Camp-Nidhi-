package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.campNidhiDataStore: DataStore<Preferences> by preferencesDataStore(name = "campnidhi_prefs")

class UserPreferencesStore(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACTIVE_CAMP_ID = stringPreferencesKey("active_camp_id")
    }

    val languageFlow: Flow<AppLanguage> = context.campNidhiDataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[Keys.LANGUAGE])
    }

    val themeModeFlow: Flow<ThemeMode> = context.campNidhiDataStore.data.map { prefs ->
        ThemeMode.fromString(prefs[Keys.THEME_MODE])
    }

    val activeCampIdFlow: Flow<String> = context.campNidhiDataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_CAMP_ID] ?: ""
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.campNidhiDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.campNidhiDataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }

    suspend fun setActiveCampId(campId: String) {
        context.campNidhiDataStore.edit { it[Keys.ACTIVE_CAMP_ID] = campId }
    }
}
