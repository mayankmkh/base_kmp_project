package dev.mayankmkh.basekmpproject.capability.posts.impl

import dev.mayankmkh.basekmpproject.capability.posts.api.PostsCommands
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsQueries
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose

public val postsCapabilityModule: Module = module {
    singleOf(::PostsLocalSource)
    single { PostsRemoteSource(get()) }
    singleOf(::PostsCapabilityImpl) onClose { it?.close() }
    // Aliases expose contracts only; onClose belongs to the implementation definition above.
    single<PostsQueries> { get<PostsCapabilityImpl>() }
    single<PostsCommands> { get<PostsCapabilityImpl>() }
}
