package dev.mayankmkh.basekmpproject.platform.securestorage

import dev.mayankmkh.basekmpproject.foundation.runtime.logEvent

// Memory only, so a page reload signs the user out. `sessionStorage` is the deferred option if the
// host page wants sessions to survive a refresh; see preferences.md sections 8.3 and 12.1. Warned,
// not informed: a shipped web build keeps warnings, and a session lost on refresh looks like a bug.
// Once per factory: this is a fact about the platform, not about any one store.
internal actual fun PlatformSecretStores.storeOpener(): (String) -> SecretStore {
    logger.w { logEvent("secret_store_memory_only", "reason" to "no_web_secure_storage") }
    return { inMemorySecretStore() }
}
