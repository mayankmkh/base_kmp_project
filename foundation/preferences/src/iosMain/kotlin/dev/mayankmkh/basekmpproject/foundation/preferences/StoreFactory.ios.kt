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
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun createPreferenceDataStore(
    context: PlatformContext,
    file: PrefFile,
    logger: Logger,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        corruptionHandler =
            replaceCorruptFile(logger, file.preferencesFileName) { emptyPreferences() },
        produceFile = { "$dataStoreDirectory/${file.preferencesFileName}".toPath() },
    )

@OptIn(ExperimentalForeignApi::class)
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
                producePath = { "$dataStoreDirectory/${file.documentFileName}".toPath() },
            ),
        corruptionHandler =
            replaceCorruptFile(logger, file.documentFileName) { serializer.defaultValue },
    )

@OptIn(ExperimentalForeignApi::class)
private val dataStoreDirectory: String by lazy {
    val applicationSupport: NSURL? =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    val path = requireNotNull(applicationSupport?.path) + "/datastore"
    check(
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    ) {
        "Could not create DataStore directory: $path"
    }
    path
}
