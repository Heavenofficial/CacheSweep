package com.example.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "whitelist_prefs")

class WhitelistRepository(private val context: Context) {
    private val whitelistKey = stringSetPreferencesKey("whitelisted_packages")

    val whitelistedPackages: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[whitelistKey] ?: emptySet()
        }

    suspend fun saveWhitelist(packages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[whitelistKey] = packages
        }
    }

    suspend fun addToWhitelist(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[whitelistKey] ?: emptySet()
            preferences[whitelistKey] = current + packageName
        }
    }

    suspend fun removeFromWhitelist(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[whitelistKey] ?: emptySet()
            preferences[whitelistKey] = current - packageName
        }
    }
}
