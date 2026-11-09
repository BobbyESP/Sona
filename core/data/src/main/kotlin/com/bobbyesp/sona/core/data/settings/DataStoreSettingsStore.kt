/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bobbyesp.sona.core.model.settings.Setting
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.model.settings.StoredValue
import com.bobbyesp.sona.core.model.settings.read
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "settings")

/** The app's settings, in one DataStore file. DataStore itself stays an implementation detail. */
fun Context.createSettingsStore(): SettingsStore =
    DataStoreSettingsStore(applicationContext.settingsDataStore)

/**
 * Every option in one Preferences DataStore. A value is looked for under its key in each of the
 * four kinds, so a setting whose kind changed between versions reads as its default instead of
 * failing (see [Setting.decode]).
 */
internal class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) :
    SettingsStore {

    private val preferences: Flow<Preferences> =
        dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    override fun <T : Any> observe(setting: Setting<T>): Flow<T> =
        preferences.map { setting.read(it.storedValue(setting.key)) }.distinctUntilChanged()

    override suspend fun <T : Any> set(setting: Setting<T>, value: T) {
        require(setting.accepts(value)) { "$setting refuses $value" }
        dataStore.edit { prefs ->
            prefs.removeAll(setting.key)
            when (val stored = setting.encode(value)) {
                is StoredValue.Bool -> prefs[booleanPreferencesKey(setting.key)] = stored.value
                is StoredValue.Int32 -> prefs[intPreferencesKey(setting.key)] = stored.value
                is StoredValue.Int64 -> prefs[longPreferencesKey(setting.key)] = stored.value
                is StoredValue.Str -> prefs[stringPreferencesKey(setting.key)] = stored.value
            }
        }
    }

    override suspend fun reset(setting: Setting<*>) {
        dataStore.edit { it.removeAll(setting.key) }
    }
}

private fun Preferences.storedValue(key: String): StoredValue? {
    val value = asMap().entries.firstOrNull { it.key.name == key }?.value ?: return null
    return when (value) {
        is Boolean -> StoredValue.Bool(value)
        is Int -> StoredValue.Int32(value)
        is Long -> StoredValue.Int64(value)
        is String -> StoredValue.Str(value)
        else -> null
    }
}

private fun androidx.datastore.preferences.core.MutablePreferences.removeAll(key: String) {
    remove(booleanPreferencesKey(key))
    remove(intPreferencesKey(key))
    remove(longPreferencesKey(key))
    remove(stringPreferencesKey(key))
}
