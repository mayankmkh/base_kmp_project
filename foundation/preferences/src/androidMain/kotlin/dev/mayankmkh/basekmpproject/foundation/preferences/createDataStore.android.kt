package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

internal actual fun createDataStore(
    prefContext: PrefContext,
    prefFile: PrefFile,
): DataStore<Preferences> = createDataStore {
    prefContext.appContext.filesDir.resolve(prefFile.dataStoreFileName).absolutePath
}
