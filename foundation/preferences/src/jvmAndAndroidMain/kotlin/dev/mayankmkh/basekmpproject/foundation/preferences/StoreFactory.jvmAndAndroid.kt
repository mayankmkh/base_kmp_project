package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import java.io.File
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

internal actual fun createPreferenceDataStore(
    context: PlatformContext,
    file: PrefFile,
    logger: Logger,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = replaceCorruptFile(logger, file, "preferences") { emptyPreferences() },
        produceFile = { context.storePath(file.preferencesFileName) },
    )

internal actual fun <T> createDocumentDataStore(
    context: PlatformContext,
    file: PrefFile,
    serializer: OkioSerializer<T>,
    logger: Logger,
): DataStore<T> =
    DataStoreFactory.create(
        storage =
            OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = serializer,
                producePath = { context.storePath(file.documentFileName) },
            ),
        corruptionHandler =
            replaceCorruptFile(logger, file, "document") { serializer.defaultValue },
    )

/** The directory this platform keeps DataStore files in. Created here, on the DataStore scope. */
internal expect fun PlatformContext.dataStoreDirectory(): File

// `java.nio.file` needs API 26; `mkdirs` is the common denominator with Android's minSdk 24.
private fun PlatformContext.storePath(fileName: String): Path {
    val directory = dataStoreDirectory()
    check(directory.mkdirs() || directory.isDirectory) {
        "Could not create DataStore directory: ${directory.absolutePath}"
    }
    return directory.resolve(fileName).absolutePath.toPath()
}
