package dev.mayankmkh.basekmpproject.shared.libs.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

/** Gets the singleton DataStore instance, creating it if necessary. */
internal fun createDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { producePath().toPath() })

internal expect fun createDataStorePath(prefContext: PrefContext, prefFile: PrefFile): String

internal fun createDataStore(prefContext: PrefContext, prefFile: PrefFile): DataStore<Preferences> =
    createDataStore {
        createDataStorePath(prefContext, prefFile)
    }
