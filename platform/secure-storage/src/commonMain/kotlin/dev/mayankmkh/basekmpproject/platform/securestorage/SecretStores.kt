package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.OpenNameRegistry
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext

/**
 * Opens the app's platform secret namespaces.
 *
 * A logger is a property of the process, not of a store, so the app builds one factory and every
 * store it opens writes through the same tagged logger.
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
 * The production factory: the platform's own secret store.
 *
 * [logger] is the app's logger; the module tags it with its own name and writes only the decisions
 * that would otherwise be invisible. No secret, key or file content is ever written to it.
 */
public fun secretStores(context: PlatformContext, logger: Logger): SecretStores =
    PlatformSecretStores(context, logger)

/** For tests and previews: process memory only, and no store name is registered. */
public fun inMemorySecretStores(): SecretStores = InMemorySecretStores

internal class PlatformSecretStores(internal val context: PlatformContext, logger: Logger) :
    SecretStores {
    internal val logger: Logger = logger.withTag(LogTag)
    private val openStore = storeOpener()

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

/** The tag every line this module writes carries. */
internal const val LogTag: String = "secure-storage"

/**
 * The platform's own opener, built once with the factory. A platform that has one fact to state
 * about itself, as web does, states it here rather than once per store; a platform that decides
 * nothing, as iOS does, never reads the logger at all.
 */
internal expect fun PlatformSecretStores.storeOpener(): (String) -> SecretStore
