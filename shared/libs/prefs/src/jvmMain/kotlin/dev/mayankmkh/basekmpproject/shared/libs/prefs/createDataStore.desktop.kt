package dev.mayankmkh.basekmpproject.shared.libs.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

internal actual fun createDataStore(
    prefContext: PrefContext,
    prefFile: PrefFile,
): DataStore<Preferences> = createDataStore {
    File(System.getProperty("java.io.tmpdir"), prefFile.dataStoreFileName).absolutePath
}
