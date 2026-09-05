package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.Logger
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

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
        selectKeysetVault(applicationId, directory, KeysetFallbackWarning(logger))
    }

/**
 * Says once that the keyset left the OS vault for an owner-only cleartext file.
 *
 * Once, because the answer never changes for the rest of the process and every store that opens
 * afterwards would repeat it. The reason is the backend's own message, which names the library and
 * the operation; the keyset itself is never part of it.
 */
internal class KeysetFallbackWarning(private val logger: Logger) {
    private val warned = AtomicBoolean(false)

    fun warn(reason: String) {
        if (!warned.compareAndSet(false, true)) return
        val oneLineReason = reason.replace(Regex("\\s+"), " ").trim()
        logger.w {
            "keyset_vault_fallback" +
                " reason=\"$oneLineReason\"" +
                " using an owner-only cleartext keyset file"
        }
    }
}

private fun selectKeysetVault(
    applicationId: String,
    directory: File,
    warning: KeysetFallbackWarning,
): KeysetVault {
    val fallback = FileKeysetVault(directory.resolve(KeysetFileName))
    val osName = System.getProperty("os.name").orEmpty()
    return when {
        osName.lowercase(Locale.ROOT).contains("mac") ->
            loadNativeVault("macOS Keychain", fallback, warning) {
                FallbackKeysetVault(
                    MacOsKeychainKeysetVault("$applicationId.secure-storage"),
                    fallback,
                    warning,
                )
            }
        osName.lowercase(Locale.ROOT).contains("win") ->
            FallbackKeysetVault(
                WindowsDpapiKeysetVault(directory.resolve(KeysetFileName)),
                fallback,
                warning,
            )
        osName.lowercase(Locale.ROOT).contains("linux") ->
            loadNativeVault("libsecret-1", fallback, warning) {
                FallbackKeysetVault(LibSecretKeysetVault(applicationId), fallback, warning)
            }
        else -> fallback.also { warning.warn("unsupported operating system '$osName'") }
    }
}

private inline fun loadNativeVault(
    backend: String,
    fallback: KeysetVault,
    warning: KeysetFallbackWarning,
    load: () -> KeysetVault,
): KeysetVault =
    try {
        load()
    } catch (failure: IllegalArgumentException) {
        fallback.also { warning.warn("$backend could not be loaded: ${failure.message}") }
    } catch (failure: SecurityException) {
        fallback.also { warning.warn("$backend could not be loaded: ${failure.message}") }
    } catch (failure: LinkageError) {
        fallback.also { warning.warn("$backend could not be loaded: ${failure.message}") }
    }

/** Switches permanently to the file if a native backend becomes unavailable during a call. */
private class FallbackKeysetVault(
    primary: KeysetVault,
    private val fallback: KeysetVault,
    private val warning: KeysetFallbackWarning,
) : KeysetVault {
    @Volatile private var active = primary

    override fun read(): String? = withFallback { it.read() }

    override fun write(json: String) {
        withFallback { it.write(json) }
    }

    private inline fun <T> withFallback(operation: (KeysetVault) -> T): T {
        val selected = active
        return try {
            operation(selected)
        } catch (failure: KeysetVaultUnavailableException) {
            val replacement = switchToFallback(failure)
            operation(replacement)
        }
    }

    private fun switchToFallback(failure: KeysetVaultUnavailableException): KeysetVault =
        synchronized(this) {
            if (active !== fallback) {
                active = fallback
                warning.warn(failure.message.orEmpty())
            }
            fallback
        }
}

private const val KeysetFileName = "secure-storage.keyset"
