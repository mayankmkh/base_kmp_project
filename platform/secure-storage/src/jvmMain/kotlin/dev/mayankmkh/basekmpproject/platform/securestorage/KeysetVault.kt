package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.logEvent
import java.io.File
import java.io.IOException
import java.nio.file.DirectoryIteratorException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Holds the cleartext Tink keyset outside the encrypted DataStore file. */
internal interface KeysetVault {
    fun read(): String?

    fun write(json: String)
}

/** Signals that an OS vault is not present, rather than that it rejected an operation. */
internal class KeysetVaultUnavailableException(message: String, cause: Throwable) :
    RuntimeException(message, cause)

private val applicationVaults = ConcurrentHashMap<String, KeysetVault>()

internal fun applicationKeysetVault(
    applicationId: String,
    directory: File,
    logger: Logger,
): KeysetVault =
    applicationVaults.computeIfAbsent(applicationId) {
        selectKeysetVault(applicationId, directory, logger)
    }

/**
 * The keyset left the OS vault for an owner-only cleartext file. Said once per process: selection
 * is memoised per application id and the fallback switch happens at most once. The reason is the
 * backend's own message, which names the library and the operation; the keyset is never part of it.
 */
private fun warnAboutFallback(logger: Logger, reason: String) {
    logger.w {
        logEvent(
            "keyset_vault_fallback",
            "reason" to reason.replace(Regex("\\s+"), " ").trim(),
            "using" to "owner-only cleartext keyset file",
        )
    }
}

private fun selectKeysetVault(
    applicationId: String,
    directory: File,
    logger: Logger,
): KeysetVault {
    val fallback = FileKeysetVault(fallbackKeysetFile(directory))
    val osName = System.getProperty("os.name").orEmpty()
    val native =
        when {
            osName.lowercase(Locale.ROOT).contains("mac") ->
                loadNativeVault("macOS Keychain") {
                    MacOsKeychainKeysetVault("$applicationId.secure-storage")
                }
            osName.lowercase(Locale.ROOT).contains("win") -> {
                moveSharedDpapiKeyset(directory)
                WindowsDpapiKeysetVault(dpapiKeysetFile(directory))
            }
            osName.lowercase(Locale.ROOT).contains("linux") ->
                loadNativeVault("libsecret-1") { LibSecretKeysetVault(applicationId) }
            else ->
                return fallback.also {
                    warnAboutFallback(logger, "unsupported operating system '$osName'")
                }
        }
    return FallbackKeysetVault(native, fallback, logger) { directory.hasEncryptedSecrets() }
}

/**
 * A backend that cannot load is unavailable from its first call rather than replaced up front, so
 * [FallbackKeysetVault] decides, with the same rule as a later outage, whether the file may stand
 * in for it.
 */
private inline fun loadNativeVault(backend: String, load: () -> KeysetVault): KeysetVault =
    try {
        load()
    } catch (failure: IllegalArgumentException) {
        UnloadableKeysetVault(backend, failure)
    } catch (failure: SecurityException) {
        UnloadableKeysetVault(backend, failure)
    } catch (failure: LinkageError) {
        UnloadableKeysetVault(backend, failure)
    }

private class UnloadableKeysetVault(backend: String, cause: Throwable) : KeysetVault {
    private val failure =
        KeysetVaultUnavailableException("$backend could not be loaded: ${cause.message}", cause)

    override fun read(): String? = throw failure

    override fun write(json: String) = throw failure
}

/**
 * Prefers the native backend and switches permanently to the file only where that cannot cost a
 * secret.
 *
 * A keyset Tink cannot find is a keyset it generates, and secrets under the old one then fail to
 * decrypt, which the store answers by replacing their file. So while the native backend is
 * unavailable, the file stands in only if it holds the keyset or no encrypted secrets exist yet;
 * otherwise their keyset is in the native backend, and the read fails with [SecretStoreException],
 * leaving every file alone, until it returns.
 *
 * The file is written only by such an outage, when no secret was stored yet, so a keyset in the
 * file is the one every secret stored since is under, and it stays in use whatever the native
 * backend answers later. The native backend can still hold an older keyset: a first launch that
 * only read the store put one there without storing a secret under it.
 */
internal class FallbackKeysetVault(
    primary: KeysetVault,
    private val fallback: FileKeysetVault,
    private val logger: Logger,
    private val hasEncryptedSecrets: () -> Boolean,
) : KeysetVault {
    @Volatile private var active = primary

    override fun read(): String? {
        val selected = active
        return if (selected === fallback) fallback.read() else readPreferring(selected)
    }

    // The file vault reports its own failures as `SecretStoreException`, never as unavailable.
    private fun readPreferring(native: KeysetVault): String? {
        fallback.read()?.let { stored ->
            // Only a keyset an outage wrote is adopted. Anything else there, a damaged file or
            // on Windows a protected keyset still waiting for its move, would be this process's
            // keyset for good if taken for cleartext, so the read refuses and leaves it in place.
            if (!stored.isCleartextKeyset()) {
                throw SecretStoreException(
                    "The keyset file ${fallback.file.absolutePath} is not a cleartext Tink " +
                        "keyset; it is left in place and not used."
                )
            }
            switchToFallback("the keyset was written to the file during an earlier outage")
            return stored
        }
        return try {
            native.read()
        } catch (failure: KeysetVaultUnavailableException) {
            readInPlaceOf(failure)
        }
    }

    override fun write(json: String) {
        try {
            active.write(json)
        } catch (failure: KeysetVaultUnavailableException) {
            switchToFallback(failure.message.orEmpty())
            fallback.write(json)
        }
    }

    /** The file holds no keyset, so it may stand in only while no secret is stored. */
    private fun readInPlaceOf(failure: KeysetVaultUnavailableException): String? {
        if (hasEncryptedSecrets()) {
            throw SecretStoreException(
                "The keyset for the stored secrets is in an unavailable OS vault: " +
                    failure.message.orEmpty(),
                failure,
            )
        }
        switchToFallback(failure.message.orEmpty())
        return null
    }

    private fun switchToFallback(reason: String) {
        synchronized(this) {
            if (active !== fallback) {
                active = fallback
                warnAboutFallback(logger, reason)
            }
        }
    }
}

private const val KeysetFileName = "secure-storage.keyset"

/**
 * Windows DPAPI's protected keyset, in a file of its own. Shared with the cleartext fallback, a
 * DPAPI outage read the protected blob as a cleartext keyset, and the fallback's first write
 * replaced the protected copy with cleartext that DPAPI could not unprotect once it loaded again.
 */
private const val DpapiKeysetFileName = "secure-storage.keyset.dpapi"

internal fun fallbackKeysetFile(directory: File): File = directory.resolve(KeysetFileName)

internal fun dpapiKeysetFile(directory: File): File = directory.resolve(DpapiKeysetFileName)

/**
 * An install from before the DPAPI keyset had a file of its own keeps the protected keyset in the
 * shared file. It moves once, decided by what the file holds rather than by asking DPAPI: a
 * cleartext keyset an outage wrote there stays put as the fallback's, and anything else is the
 * protected keyset, which moves even while DPAPI fails, so a transient DPAPI failure can never
 * leave it where the fallback would read it.
 */
internal fun moveSharedDpapiKeyset(directory: File) {
    val shared = fallbackKeysetFile(directory)
    val dpapi = dpapiKeysetFile(directory)
    if (dpapi.exists() || !shared.isFile) return
    try {
        if (FileKeysetVault(shared).read()?.isCleartextKeyset() == false) {
            vaultFileOperation("move", shared) {
                Files.move(shared.toPath(), dpapi.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
        }
    } catch (_: SecretStoreException) {
        // Left where it is: the fallback refuses a file it cannot read as cleartext, and the next
        // launch tries the move again.
    }
}

/**
 * Whether [this] is a cleartext keyset, decided positively: Tink's JSON keyset format is a JSON
 * object, while a DPAPI blob opens with a binary header and never parses as JSON.
 */
internal fun String.isCleartextKeyset(): Boolean =
    try {
        Json.parseToJsonElement(this) is JsonObject
    } catch (_: SerializationException) {
        false
    }

internal const val SecretsFileSuffix = ".secrets"

/**
 * Whether any store has written its encrypted file here. Only a directory that does not exist is
 * known to hold none: one that cannot be listed may hold secrets, and answering "none" would let a
 * new keyset replace theirs, so it throws [SecretStoreException] instead.
 */
internal fun File.hasEncryptedSecrets(): Boolean =
    try {
        Files.newDirectoryStream(toPath()).use { entries ->
            entries.any {
                Files.isRegularFile(it) && it.fileName.toString().endsWith(SecretsFileSuffix)
            }
        }
    } catch (_: NoSuchFileException) {
        false
    } catch (failure: IOException) {
        throw unlistable(this, failure)
    } catch (failure: DirectoryIteratorException) {
        throw unlistable(this, failure)
    }

private fun unlistable(directory: File, failure: Exception) =
    SecretStoreException(
        "Could not tell whether secrets are stored in ${directory.absolutePath}.",
        failure,
    )
