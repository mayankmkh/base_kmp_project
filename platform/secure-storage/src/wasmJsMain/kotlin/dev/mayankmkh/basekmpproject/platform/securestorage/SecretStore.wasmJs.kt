package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.logEvent

// Memory only, so a page reload signs the user out. `sessionStorage` is the deferred option if the
// host page wants sessions to survive a refresh; see preferences.md sections 8.3 and 12.1. Warned,
// not informed: a shipped web build keeps warnings, and a session lost on refresh looks like a bug.
internal actual fun createSecretStore(
    context: PlatformContext,
    name: String,
    logger: Logger,
): SecretStore {
    logger.w {
        logEvent("secret_store_memory_only", "name" to name, "reason" to "no_web_secure_storage")
    }
    return inMemorySecretStore()
}
