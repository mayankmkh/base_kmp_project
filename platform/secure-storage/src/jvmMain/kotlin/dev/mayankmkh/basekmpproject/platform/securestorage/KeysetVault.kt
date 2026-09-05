package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.logEvent
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
    val fallback = FileKeysetVault(directory.resolve(KeysetFileName))
    val osName = System.getProperty("os.name").orEmpty()
    return when {
        osName.lowercase(Locale.ROOT).contains("mac") ->
            loadNativeVault("macOS Keychain", fallback, logger) {
                FallbackKeysetVault(
                    MacOsKeychainKeysetVault("$applicationId.secure-storage"),
                    fallback,
                    logger,
                )
            }
        osName.lowercase(Locale.ROOT).contains("win") ->
            FallbackKeysetVault(
                WindowsDpapiKeysetVault(directory.resolve(KeysetFileName)),
                fallback,
                logger,
            )
        osName.lowercase(Locale.ROOT).contains("linux") ->
            loadNativeVault("libsecret-1", fallback, logger) {
                FallbackKeysetVault(LibSecretKeysetVault(applicationId), fallback, logger)
            }
        else ->
            fallback.also { warnAboutFallback(logger, "unsupported operating system '$osName'") }
    }
}

private inline fun loadNativeVault(
    backend: String,
    fallback: KeysetVault,
    logger: Logger,
    load: () -> KeysetVault,
): KeysetVault =
    try {
        load()
    } catch (failure: IllegalArgumentException) {
        fallback.also {
            warnAboutFallback(logger, "$backend could not be loaded: ${failure.message}")
        }
    } catch (failure: SecurityException) {
        fallback.also {
            warnAboutFallback(logger, "$backend could not be loaded: ${failure.message}")
        }
    } catch (failure: LinkageError) {
        fallback.also {
            warnAboutFallback(logger, "$backend could not be loaded: ${failure.message}")
        }
    }

/** Switches permanently to the file if a native backend becomes unavailable during a call. */
private class FallbackKeysetVault(
    primary: KeysetVault,
    private val fallback: KeysetVault,
    private val logger: Logger,
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
                warnAboutFallback(logger, failure.message.orEmpty())
            }
            fallback
        }
}

private const val KeysetFileName = "secure-storage.keyset"
