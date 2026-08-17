package dev.mayankmkh.basekmpproject.shared.libs.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer

/**
 * `localStorage`, not the `sessionStorage` that `PreferenceDataStoreFactory.createWithPath` picks
 * on this platform: preferences are expected to outlive the tab.
 */
internal actual fun createDataStore(
    prefContext: PrefContext,
    prefFile: PrefFile,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        storage =
            WebLocalStorage(serializer = PreferencesSerializer, name = prefFile.dataStoreFileName)
    )
