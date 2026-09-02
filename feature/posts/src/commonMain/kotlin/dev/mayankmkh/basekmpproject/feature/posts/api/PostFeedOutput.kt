package dev.mayankmkh.basekmpproject.feature.posts.api

import dev.mayankmkh.basekmpproject.capability.posts.api.PostId

/** Navigation intents raised by the post feed feature; the host decides what they mean. */
public sealed interface PostFeedOutput {
    public data class OpenPost(val id: PostId) : PostFeedOutput
}
