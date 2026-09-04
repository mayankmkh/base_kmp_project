package dev.mayankmkh.basekmpproject.platform.securestorage

import java.io.File
import java.io.IOException

internal class FileKeysetVault(private val file: File) : KeysetVault {
    override fun read(): String? =
        vaultFileOperation("read", file) { readOwnerOnlyFile(file)?.decodeToString() }

    override fun write(json: String) {
        vaultFileOperation("write", file) { writeOwnerOnlyFile(file, json.encodeToByteArray()) }
    }
}

internal inline fun <T> vaultFileOperation(operation: String, file: File, block: () -> T): T =
    try {
        block()
    } catch (failure: SecretStoreException) {
        throw failure
    } catch (failure: IOException) {
        throw SecretStoreException(
            "Secret keyset file $operation failed for ${file.absolutePath}.",
            failure,
        )
    } catch (failure: SecurityException) {
        throw SecretStoreException(
            "Secret keyset file $operation failed for ${file.absolutePath}.",
            failure,
        )
    }
