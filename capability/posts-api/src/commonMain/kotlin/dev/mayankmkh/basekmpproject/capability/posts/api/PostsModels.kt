package dev.mayankmkh.basekmpproject.capability.posts.api

import kotlin.jvm.JvmInline

@JvmInline public value class PostId(public val value: Long)

public data class Post(
    val id: PostId,
    val authorId: Long,
    val title: String,
    val body: String,
)

public data class PostFeed(val posts: List<Post>)
