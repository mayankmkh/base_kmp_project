package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

/** Gets the singleton DataStore instance, creating it if necessary. */
internal fun createDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { producePath().toPath() })

/**
 * The store backing one [PrefFile].
 *
 * Every platform with a filesystem answers this by pointing [createDataStore] at a path. The
 * browser has no path to point at, so the expectation is on the store, not on the path.
 */
internal expect fun createDataStore(
    prefContext: PrefContext,
    prefFile: PrefFile,
): DataStore<Preferences>
