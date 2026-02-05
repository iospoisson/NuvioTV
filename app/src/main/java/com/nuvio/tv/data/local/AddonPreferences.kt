package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "addon_preferences")

@Singleton
class AddonPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val addonUrlsKey = stringSetPreferencesKey("installed_addon_urls")
    private val addonUrlsOrderedKey = stringPreferencesKey("installed_addon_urls_ordered")

    val installedAddonUrls: Flow<List<String>> = context.dataStore.data
        .map { preferences ->
            val ordered = preferences[addonUrlsOrderedKey]
            when {
                !ordered.isNullOrBlank() -> parseOrderedUrls(ordered)
                preferences[addonUrlsKey] != null -> preferences[addonUrlsKey]!!.toList()
                else -> getDefaultAddons()
            }
        }

    suspend fun addAddon(url: String) {
        context.dataStore.edit { preferences ->
            val currentUrls = getCurrentOrderedUrls(preferences)
            if (!currentUrls.contains(url)) {
                val updated = currentUrls + url
                preferences[addonUrlsOrderedKey] = serializeOrderedUrls(updated)
                preferences[addonUrlsKey] = updated.toSet()
            }
        }
    }

    suspend fun removeAddon(url: String) {
        context.dataStore.edit { preferences ->
            val currentUrls = getCurrentOrderedUrls(preferences)
            val updated = currentUrls.filterNot { it == url }
            preferences[addonUrlsOrderedKey] = serializeOrderedUrls(updated)
            preferences[addonUrlsKey] = updated.toSet()
        }
    }

    suspend fun setAddonOrder(urls: List<String>) {
        context.dataStore.edit { preferences ->
            val cleaned = urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            preferences[addonUrlsOrderedKey] = serializeOrderedUrls(cleaned)
            preferences[addonUrlsKey] = cleaned.toSet()
        }
    }

    private fun getCurrentOrderedUrls(preferences: Preferences): List<String> {
        val ordered = preferences[addonUrlsOrderedKey]
        return when {
            !ordered.isNullOrBlank() -> parseOrderedUrls(ordered)
            preferences[addonUrlsKey] != null -> preferences[addonUrlsKey]!!.toList()
            else -> getDefaultAddons()
        }
    }

    private fun parseOrderedUrls(value: String): List<String> =
        value.split("\n").map { it.trim() }.filter { it.isNotBlank() }

    private fun serializeOrderedUrls(urls: List<String>): String =
        urls.joinToString("\n")

    private fun getDefaultAddons(): List<String> = listOf(
        "https://v3-cinemeta.strem.io"
    )
}
