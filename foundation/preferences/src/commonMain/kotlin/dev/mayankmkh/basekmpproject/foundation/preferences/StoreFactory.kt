package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Storage
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.OpenNameRegistry
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.causeClassName
import dev.mayankmkh.basekmpproject.foundation.runtime.logEvent
import kotlinx.coroutines.CoroutineScope

internal val PrefFile.preferencesFileName: String
    get() = "$name.preferences_pb"

internal val PrefFile.documentFileName: String
    get() = "$name.json"

// Store instances are process-lifetime objects. Registering the name makes a collision fail during
// graph creation instead of after the first asynchronous DataStore read.
private val openFiles = OpenNameRegistry("store for file")

internal fun registerOpenFile(file: PrefFile): Unit = openFiles.register(file.name)

/**
 * The whole platform seam (section 4): where the bytes of one named store live. Everything above
 * it, the corruption handler, the serializers and the default value, is assembled once below.
 */
internal expect fun <T> storageFor(
    context: PlatformContext,
    fileName: String,
    serializer: OkioSerializer<T>,
): Storage<T>

// A null `scope` keeps DataStore's own default. A test passes one so it can end an instance before
// reopening its file: DataStore allows one active instance per file per process.
internal fun preferenceDataStore(
    context: PlatformContext,
    file: PrefFile,
    logger: Logger,
    scope: CoroutineScope? = null,
): DataStore<Preferences> {
    val storage = storageFor(context, file.preferencesFileName, PreferencesSerializer)
    val handler = replaceCorruptFile(logger, file.preferencesFileName) { emptyPreferences() }
    return if (scope == null) {
        PreferenceDataStoreFactory.create(storage = storage, corruptionHandler = handler)
    } else {
        PreferenceDataStoreFactory.create(
            storage = storage,
            corruptionHandler = handler,
            scope = scope,
        )
    }
}

internal fun <T> documentDataStore(
    context: PlatformContext,
    file: PrefFile,
    serializer: OkioSerializer<T>,
    logger: Logger,
    scope: CoroutineScope? = null,
): DataStore<T> {
    val storage = storageFor(context, file.documentFileName, serializer)
    val handler = replaceCorruptFile(logger, file.documentFileName) { serializer.defaultValue }
    return if (scope == null) {
        DataStoreFactory.create(storage = storage, corruptionHandler = handler)
    } else {
        DataStoreFactory.create(storage = storage, corruptionHandler = handler, scope = scope)
    }
}

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
// fork moving an existing app off SharedPreferences adds an optional parameter to the two builders
// above; nothing in the template needs it yet.
