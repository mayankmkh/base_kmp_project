package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.preferences.core.Preferences
import dev.mayankmkh.basekmpproject.foundation.runtime.OpenNameRegistry
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext

internal val PrefFile.preferencesFileName: String
    get() = "$name.preferences_pb"

internal val PrefFile.documentFileName: String
    get() = "$name.json"

// Store instances are process-lifetime objects. Registering the name makes a collision fail during
// graph creation instead of after the first asynchronous DataStore read.
private val openFiles = OpenNameRegistry("store for file")

internal fun registerOpenFile(file: PrefFile): Unit = openFiles.register(file.name)

// Deferred (preferences.md section 12.1): DataStore's `migrations` parameter is not exposed. A
// fork moving an existing app off SharedPreferences adds an optional parameter to the open
// functions here; nothing in the template needs it yet.

internal expect fun createPreferenceDataStore(
    context: PlatformContext,
    file: PrefFile,
): DataStore<Preferences>

internal expect fun <T> createDocumentDataStore(
    context: PlatformContext,
    file: PrefFile,
    serializer: OkioSerializer<T>,
): DataStore<T>
