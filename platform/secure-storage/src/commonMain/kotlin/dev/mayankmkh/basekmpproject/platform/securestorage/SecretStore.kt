package dev.mayankmkh.basekmpproject.platform.securestorage

import kotlinx.coroutines.flow.Flow

public interface SecretStore {
    public suspend fun get(key: String): String?

    public fun observe(key: String): Flow<String?>

    public suspend fun set(key: String, value: String)

    public suspend fun remove(key: String)

    public suspend fun clear()
}

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
