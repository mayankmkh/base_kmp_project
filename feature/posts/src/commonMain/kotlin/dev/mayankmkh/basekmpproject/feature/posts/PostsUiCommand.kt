package dev.mayankmkh.basekmpproject.feature.posts

import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind

/** Transient feedback the Posts screens show once, outside their state. */
internal sealed interface PostsUiCommand {
    data class ShowRefreshFailed(val kind: ProblemKind) : PostsUiCommand
}
