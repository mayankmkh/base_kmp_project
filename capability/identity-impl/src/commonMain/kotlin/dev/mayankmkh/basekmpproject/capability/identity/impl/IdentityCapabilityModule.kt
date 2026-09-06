package dev.mayankmkh.basekmpproject.capability.identity.impl

import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityCommands
import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityQueries
import dev.mayankmkh.basekmpproject.foundation.network.CredentialProvider
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Loads the Identity Capability. Its [CredentialProvider] binding supplies the neutral network
 * inversion through App composition (§18.6.1); `AnonymousCredentialProvider` remains available to
 * tests and apps without sign-in. [IdentityQueries] and [IdentityCommands] await the sign-in
 * Feature.
 */
public val identityCapabilityModule: Module = module {
    singleOf(::CredentialStore)
    singleOf(::IdentityCapabilityImpl)
    // Aliases expose contracts only; lifecycle hooks belong to implementation definitions.
    single<IdentityQueries> { get<IdentityCapabilityImpl>() }
    single<IdentityCommands> { get<IdentityCapabilityImpl>() }
    single<CredentialProvider> { get<IdentityCapabilityImpl>() }
}
