package dev.mayankmkh.basekmpproject.feature.posts.api

import dev.mayankmkh.basekmpproject.feature.posts.PostDetailViewModel
import dev.mayankmkh.basekmpproject.feature.posts.PostFeedViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

public val postsFeatureModule: Module = module {
    viewModel { parameters ->
        PostFeedViewModel(
            instanceKey = parameters.get(),
            queries = get(),
            commands = get(),
        )
    }
    viewModel { parameters ->
        PostDetailViewModel(
            postId = parameters.get(),
            instanceKey = parameters.get(),
            queries = get(),
            commands = get(),
        )
    }
}
