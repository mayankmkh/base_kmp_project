package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext

// Memory only, so a page reload signs the user out. `sessionStorage` is the deferred option if the
// host page wants sessions to survive a refresh; see preferences.md sections 8.3 and 12.1. The line
// says so once per store, because a session that disappears on refresh otherwise looks like a bug.
internal actual fun createSecretStore(
    context: PlatformContext,
    name: String,
    logger: Logger,
): SecretStore {
    logger.i { "secret_store_memory_only name=$name reason=no_web_secure_storage" }
    return inMemorySecretStore()
}
