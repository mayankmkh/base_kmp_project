package dev.mayankmkh.basekmpproject.platform.securestorage

import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext

// Memory only, so a page reload signs the user out. `sessionStorage` is the deferred option if the
// host page wants sessions to survive a refresh; see preferences.md sections 8.3 and 12.1.
internal actual fun createSecretStore(context: PlatformContext, name: String): SecretStore =
    inMemorySecretStore()
