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
 * tests and apps without sign-in.
 */
public val identityCapabilityModule: Module = module {
    singleOf(::CredentialStore)
    single { IdentityCapabilityImpl(get()) }
    single<IdentityQueries> { get<IdentityCapabilityImpl>() }
    single<IdentityCommands> { get<IdentityCapabilityImpl>() }
    single<CredentialProvider> { get<IdentityCapabilityImpl>() }
}
