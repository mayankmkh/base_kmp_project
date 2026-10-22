package dev.mayankmkh.basekmpproject.capability.identity.impl

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.capability.identity.api.AuthToken
import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityCommands
import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityQueries
import dev.mayankmkh.basekmpproject.foundation.network.CredentialProvider
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStores
import dev.mayankmkh.basekmpproject.platform.securestorage.inMemorySecretStores
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class IdentityCapabilityModuleTest {
    @Test
    fun `the credential provider shares credential state with the capability`() = runTest {
        val application = koinApplication {
            modules(identityCapabilityModule, testSecretStoresModule)
        }
        try {
            val commands: IdentityCommands =
                checkNotNull(application.koin.getOrNull()) { "IdentityCommands binding missing" }
            val provider: CredentialProvider =
                checkNotNull(application.koin.getOrNull()) { "CredentialProvider binding missing" }
            val queries: IdentityQueries =
                checkNotNull(application.koin.getOrNull()) { "IdentityQueries binding missing" }
            // The provider is a separate object, so the client never depends on the capability;
            // the queries and the commands stay one instance.
            assertSame<Any>(commands, queries)

            commands.signIn(AuthToken("binding-test-token"))
            assertEquals("binding-test-token", provider.currentCredential())
            commands.signOut()
            assertNull(provider.currentCredential())
        } finally {
            application.close()
        }
    }
}

private val testSecretStoresModule = module {
    single<SecretStores> { inMemorySecretStores() }
    single<Logger> { Logger }
}
