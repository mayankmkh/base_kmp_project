package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.OpenNameRegistry
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext

/**
 * Opens the app's platform secret namespaces. The app builds one factory from its logger and
 * Capability implementations take it to open the namespaces they own (preferences.md section 12).
 */
public interface SecretStores {
    /**
     * Opens one platform secret namespace.
     *
     * Android keeps the Tink keyset in `<applicationId>.secure-storage`; app backup rules can use
     * this stable name to exclude the keyset alongside the non-backed-up ciphertext.
     */
    public fun open(name: String): SecretStore
}

/**
 * The production factory: the platform's own secret store. It writes only the decisions that would
 * otherwise be invisible; no secret, key or file content is ever logged.
 */
public fun secretStores(context: PlatformContext, logger: Logger): SecretStores =
    PlatformSecretStores(context, logger)

/** For tests and previews: process memory only, and no store name is registered. */
public fun inMemorySecretStores(): SecretStores = InMemorySecretStores

internal class PlatformSecretStores(context: PlatformContext, logger: Logger) : SecretStores {
    private val openStore = secretStoreOpener(context, logger.withTag("secure-storage"))

    override fun open(name: String): SecretStore {
        openStores.register(name)
        return openStore(name)
    }
}

// No registration: a test that opens the same name twice is asking for two independent stores, and
// nothing here is process-lifetime state.
private object InMemorySecretStores : SecretStores {
    override fun open(name: String): SecretStore = inMemorySecretStore()
}

// Same rule as `:foundation:preferences`: a store is a process-lifetime object, so a second open of
// the same name is a wiring mistake that should fail here, with the store's name in the message.
private val openStores = OpenNameRegistry("secret store named")

/**
 * The platform's opener, built once per factory: key material is prepared once and shared by every
 * store, a platform fact such as web's memory-only storage is stated once, and a platform that
 * decides nothing, as iOS does, never reads the logger.
 */
internal expect fun secretStoreOpener(
    context: PlatformContext,
    logger: Logger,
): (String) -> SecretStore
