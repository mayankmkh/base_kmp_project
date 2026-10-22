package dev.mayankmkh.basekmpproject.capability.identity.impl

import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityCommands
import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityQueries
import dev.mayankmkh.basekmpproject.foundation.network.CredentialProvider
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * Loads the Identity Capability. Its [CredentialProvider] binding supplies the neutral network
 * inversion through App composition (§18.6.1); `AnonymousCredentialProvider` remains available to
 * tests and apps without sign-in. [IdentityQueries] and [IdentityCommands] await the sign-in
 * Feature.
 *
 * The provider is [CredentialStoreProvider], deliberately not the capability object: the client
 * depends on the provider, so the provider must not depend on anything that needs the client. Both
 * share the one [CredentialStore], which is what keeps their credential state the same.
 */
public val identityCapabilityModule: Module = module {
    single<CredentialStore>()
    single<CredentialStoreProvider>() bind CredentialProvider::class
    single<IdentityCapabilityImpl>() binds arrayOf(IdentityQueries::class, IdentityCommands::class)
}
