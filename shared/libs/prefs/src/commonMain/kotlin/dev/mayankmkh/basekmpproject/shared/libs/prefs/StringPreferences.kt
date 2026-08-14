package dev.mayankmkh.basekmpproject.shared.libs.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal suspend fun DataStore<Preferences>.getStringOrNull(prefKey: PrefKey): String? =
    data.map { it[stringPreferencesKey(prefKey.key)] }.first()

// `distinctUntilChanged` is load-bearing: DataStore re-emits the whole snapshot on any write to the
// file, so without it a flow for one key also fires when an unrelated key in the same file changes.
internal fun DataStore<Preferences>.getStringOrNullFlow(prefKey: PrefKey): Flow<String?> {
    val key = stringPreferencesKey(prefKey.key)
    return data.map { it[key] }.distinctUntilChanged()
}

internal suspend fun DataStore<Preferences>.putString(prefKey: PrefKey, value: String) {
    edit { it[stringPreferencesKey(prefKey.key)] = value }
}

internal suspend fun DataStore<Preferences>.remove(prefKey: PrefKey) {
    edit { it.remove(stringPreferencesKey(prefKey.key)) }
}

internal suspend fun DataStore<Preferences>.hasKey(prefKey: PrefKey): Boolean =
    data.map { stringPreferencesKey(prefKey.key) in it }.first()
