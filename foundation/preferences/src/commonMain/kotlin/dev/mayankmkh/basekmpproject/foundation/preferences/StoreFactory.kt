package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.preferences.core.Preferences
import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.OpenNameRegistry
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.causeClassName
import dev.mayankmkh.basekmpproject.foundation.runtime.logEvent

internal val PrefFile.preferencesFileName: String
    get() = "$name.preferences_pb"

internal val PrefFile.documentFileName: String
    get() = "$name.json"

// Store instances are process-lifetime objects. Registering the name makes a collision fail during
// graph creation instead of after the first asynchronous DataStore read.
private val openFiles = OpenNameRegistry("store for file")

internal fun registerOpenFile(file: PrefFile): Unit = openFiles.register(file.name)

/**
 * The handler every store opens with (section 5): DataStore replaces a file it cannot read, and
 * this warning is the only trace that the user lost it. Neither the file's contents nor the
 * failure's own message is logged, because both can quote what the file held.
 */
internal fun <T> replaceCorruptFile(
    logger: Logger,
    fileName: String,
    defaultValue: () -> T,
): ReplaceFileCorruptionHandler<T> = ReplaceFileCorruptionHandler { failure ->
    logger.w {
        logEvent("store_file_replaced", "file" to fileName, "causeClass" to failure.causeClassName)
    }
    defaultValue()
}

// Deferred (preferences.md section 12.1): DataStore's `migrations` parameter is not exposed. A
// fork moving an existing app off SharedPreferences adds an optional parameter to the factory's
// open functions; nothing in the template needs it yet.

internal expect fun createPreferenceDataStore(
    context: PlatformContext,
    file: PrefFile,
    logger: Logger,
): DataStore<Preferences>

internal expect fun <T> createDocumentDataStore(
    context: PlatformContext,
    file: PrefFile,
    serializer: OkioSerializer<T>,
    logger: Logger,
): DataStore<T>
