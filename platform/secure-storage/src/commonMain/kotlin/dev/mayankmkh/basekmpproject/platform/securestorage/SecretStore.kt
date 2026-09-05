package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.OpenNameRegistry
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import kotlinx.coroutines.flow.Flow

public interface SecretStore {
    public suspend fun get(key: String): String?

    public fun observe(key: String): Flow<String?>

    public suspend fun set(key: String, value: String)

    public suspend fun remove(key: String)

    public suspend fun clear()
}

/**
 * Opens one platform secret namespace.
 *
 * Android keeps the Tink keyset in `<applicationId>.secure-storage`; app backup rules can use this
 * stable name to exclude the keyset alongside the non-backed-up ciphertext.
 *
 * [logger] is the app's one logger, tagged here with this module's name as every module that takes
 * a logger does. It records the decisions that would otherwise be invisible: a platform that keeps
 * secrets in memory only, a stored file that had to be replaced, and the loss of the OS keyset
 * vault. No secret, key or file content is ever written to it.
 */
public fun openSecretStore(context: PlatformContext, name: String, logger: Logger): SecretStore {
    openStores.register(name)
    return createSecretStore(context, name, logger.withTag(LogTag))
}

/** The tag every line this module writes carries. */
internal const val LogTag: String = "secure-storage"

// Same rule as `:foundation:preferences`: a store is a process-lifetime object, so a second open of
// the same name is a wiring mistake that should fail here, with the store's name in the message.
private val openStores = OpenNameRegistry("secret store named")

/**
 * The platform secret store refused an operation.
 *
 * Every actual throws this type: iOS when a Keychain call fails, with the `OSStatus` in the
 * message, most often `errSecInteractionNotAllowed` (-25308) from a read before the device's first
 * unlock; Android when DataStore's file read or write fails; and desktop when DataStore, Tink or
 * the selected keyset vault fails. The original failure is attached as the cause. A consumer that
 * wants "unavailable" to read as "no value" catches this one type.
 */
public class SecretStoreException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

internal expect fun createSecretStore(
    context: PlatformContext,
    name: String,
    logger: Logger,
): SecretStore
