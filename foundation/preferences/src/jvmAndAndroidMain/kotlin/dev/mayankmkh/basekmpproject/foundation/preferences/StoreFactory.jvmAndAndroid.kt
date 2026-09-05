package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import java.io.File
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

internal actual fun <T> storageFor(
    context: PlatformContext,
    fileName: String,
    serializer: OkioSerializer<T>,
): Storage<T> =
    OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = serializer,
        producePath = { context.storePath(fileName) },
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
