package dev.mayankmkh.basekmpproject.feature.posts.api

import dev.mayankmkh.basekmpproject.feature.posts.PostDetailViewModel
import dev.mayankmkh.basekmpproject.feature.posts.PostFeedViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

public val postsFeatureModule: Module = module {
    viewModel<PostFeedViewModel>()
    viewModel { parameters ->
        PostDetailViewModel(
            postId = parameters.get(),
            queries = get(),
            commands = get(),
        )
    }
}
