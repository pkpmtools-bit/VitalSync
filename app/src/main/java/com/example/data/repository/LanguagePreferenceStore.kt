package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localeDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "vitalsync_locale_prefs"
)

class LanguagePreferenceStore(private val context: Context) {
  private val languageKey = stringPreferencesKey("app_language")

  val languageFlow: Flow<AppLanguage> =
    context.localeDataStore.data.map { prefs ->
      AppLanguage.fromCode(prefs[languageKey])
    }

  suspend fun setLanguage(language: AppLanguage) {
    context.localeDataStore.edit { prefs ->
      prefs[languageKey] = language.code
    }
  }
}
