package dev.mayankmkh.basekmpproject.platform.securestorage

// Memory only, so a page reload signs the user out. `sessionStorage` is the deferred option if the
// host page wants sessions to survive a refresh; see preferences.md sections 8.3 and 12.1.
internal actual fun createSecretStore(context: SecureStorageContext, name: String): SecretStore =
    inMemorySecretStore()
