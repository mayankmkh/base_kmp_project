package dev.mayankmkh.basekmpproject.platform.securestorage

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import java.io.File

internal class WindowsDpapiKeysetVault(private val file: File) : KeysetVault {
    override fun read(): String? {
        val encrypted = vaultFileOperation("read", file) { readOwnerOnlyFile(file) } ?: return null
        return dpapi("unprotect") {
            Crypt32Util.cryptUnprotectData(encrypted, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
                .decodeToString()
        }
    }

    override fun write(json: String) {
        val encrypted =
            dpapi("protect") {
                Crypt32Util.cryptProtectData(
                    json.encodeToByteArray(),
                    WinCrypt.CRYPTPROTECT_UI_FORBIDDEN,
                )
            }
        vaultFileOperation("write", file) { writeOwnerOnlyFile(file, encrypted) }
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> dpapi(operation: String, block: () -> T): T =
        try {
            block()
        } catch (failure: LinkageError) {
            throw KeysetVaultUnavailableException(
                "Windows DPAPI could not be loaded: ${failure.message}",
                failure,
            )
        } catch (failure: Exception) {
            throw SecretStoreException("Windows DPAPI keyset $operation failed.", failure)
        }
}
