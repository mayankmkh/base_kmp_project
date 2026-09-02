package dev.mayankmkh.basekmpproject.feature.posts.api

/** Navigation intents raised by the post detail feature; the host decides what they mean. */
public sealed interface PostDetailOutput {
    public data object Back : PostDetailOutput
}
