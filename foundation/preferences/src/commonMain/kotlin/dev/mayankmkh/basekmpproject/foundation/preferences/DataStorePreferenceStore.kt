package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class DataStorePreferenceStore(private val dataStore: DataStore<Preferences>) :
    PreferenceStore {

    override suspend fun getString(key: PrefKey): String? =
        dataStore.data.map { it[stringPreferencesKey(key.key)] }.first()

    // `distinctUntilChanged` is load-bearing: DataStore re-emits the whole snapshot on any write
    // to the file, so without it a flow for one key also fires when an unrelated key in the same
    // file changes.
    override fun observeString(key: PrefKey): Flow<String?> {
        val preferenceKey = stringPreferencesKey(key.key)
        return dataStore.data.map { it[preferenceKey] }.distinctUntilChanged()
    }

    override suspend fun putString(key: PrefKey, value: String) {
        dataStore.edit { it[stringPreferencesKey(key.key)] = value }
    }

    override suspend fun remove(key: PrefKey) {
        dataStore.edit { it.remove(stringPreferencesKey(key.key)) }
    }

    override suspend fun contains(key: PrefKey): Boolean =
        dataStore.data.map { stringPreferencesKey(key.key) in it }.first()
}
