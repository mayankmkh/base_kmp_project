package dev.mayankmkh.basekmpproject.capability.posts.impl

import dev.mayankmkh.basekmpproject.capability.posts.api.PostsCommands
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsQueries
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.plugin.module.dsl.single

public val postsCapabilityModule: Module = module {
    single<PostsLocalSource>()
    single<PostsRemoteSource>()
    single<PostsCapabilityImpl>() onClose { it?.close() }
    // Aliases expose contracts only; onClose belongs to the implementation definition above.
    single<PostsQueries> { get<PostsCapabilityImpl>() }
    single<PostsCommands> { get<PostsCapabilityImpl>() }
}
