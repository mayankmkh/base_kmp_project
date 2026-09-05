package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun <T> storageFor(
    context: PlatformContext,
    fileName: String,
    serializer: OkioSerializer<T>,
): Storage<T> =
    OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = serializer,
        producePath = { "$dataStoreDirectory/$fileName".toPath() },
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
