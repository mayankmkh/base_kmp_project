package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class DataStorePreferenceStore(private val dataStore: DataStore<Preferences>) :
    PreferenceStore {

    override suspend fun <T> get(key: PrefKey<T>): T? = dataStore.data.first()[key.dataStoreKey]

    // DataStore emits the whole snapshot after every write. Filtering here keeps an unrelated key
    // from looking like a change to the Capability that owns this key.
    override fun <T> observe(key: PrefKey<T>): Flow<T?> =
        dataStore.data.map { it[key.dataStoreKey] }.distinctUntilChanged()

    override suspend fun <T> set(key: PrefKey<T>, value: T) {
        dataStore.edit { it[key.dataStoreKey] = value }
    }

    override suspend fun remove(key: PrefKey<*>) {
        dataStore.edit { it.remove(key.dataStoreKey) }
    }

    override suspend fun contains(key: PrefKey<*>): Boolean =
        dataStore.data.first().contains(key.dataStoreKey)

    override suspend fun edit(block: PreferenceEditor.() -> Unit) {
        dataStore.edit { DataStorePreferenceEditor(it).block() }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

private class DataStorePreferenceEditor(private val preferences: MutablePreferences) :
    PreferenceEditor {
    override fun <T> set(key: PrefKey<T>, value: T) {
        preferences[key.dataStoreKey] = value
    }

    override fun remove(key: PrefKey<*>) {
        preferences.remove(key.dataStoreKey)
    }

    override fun clear() {
        preferences.clear()
    }
}
