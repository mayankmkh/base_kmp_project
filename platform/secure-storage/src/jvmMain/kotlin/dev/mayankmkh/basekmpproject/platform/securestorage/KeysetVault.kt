package dev.mayankmkh.basekmpproject.platform.securestorage

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
private val fallbackWarningPrinted = AtomicBoolean(false)

internal fun applicationKeysetVault(applicationId: String, directory: File): KeysetVault =
    applicationVaults.computeIfAbsent(applicationId) {
        selectKeysetVault(applicationId, directory)
    }

private fun selectKeysetVault(applicationId: String, directory: File): KeysetVault {
    val fallback = FileKeysetVault(directory.resolve(KeysetFileName))
    val osName = System.getProperty("os.name").orEmpty()
    return when {
        osName.lowercase(Locale.ROOT).contains("mac") ->
            loadNativeVault("macOS Keychain", fallback) {
                FallbackKeysetVault(
                    MacOsKeychainKeysetVault("$applicationId.secure-storage"),
                    fallback,
                )
            }
        osName.lowercase(Locale.ROOT).contains("win") ->
            FallbackKeysetVault(
                WindowsDpapiKeysetVault(directory.resolve(KeysetFileName)),
                fallback,
            )
        osName.lowercase(Locale.ROOT).contains("linux") ->
            loadNativeVault("libsecret-1", fallback) {
                FallbackKeysetVault(LibSecretKeysetVault(applicationId), fallback)
            }
        else -> fallback.also { warnAboutFallback("unsupported operating system '$osName'") }
    }
}

private inline fun loadNativeVault(
    backend: String,
    fallback: KeysetVault,
    load: () -> KeysetVault,
): KeysetVault =
    try {
        load()
    } catch (failure: IllegalArgumentException) {
        fallback.also { warnAboutFallback("$backend could not be loaded: ${failure.message}") }
    } catch (failure: SecurityException) {
        fallback.also { warnAboutFallback("$backend could not be loaded: ${failure.message}") }
    } catch (failure: LinkageError) {
        fallback.also { warnAboutFallback("$backend could not be loaded: ${failure.message}") }
    }

/** Switches permanently to the file if a native backend becomes unavailable during a call. */
private class FallbackKeysetVault(
    primary: KeysetVault,
    private val fallback: KeysetVault,
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
                warnAboutFallback(failure.message.orEmpty())
            }
            fallback
        }
}

private fun warnAboutFallback(reason: String) {
    if (fallbackWarningPrinted.compareAndSet(false, true)) {
        val oneLineReason = reason.replace(Regex("\\s+"), " ").trim()
        System.err.println(
            "Warning: secure-storage OS keyset vault unavailable ($oneLineReason); " +
                "using an owner-only cleartext keyset file."
        )
    }
}

private const val KeysetFileName = "secure-storage.keyset"
