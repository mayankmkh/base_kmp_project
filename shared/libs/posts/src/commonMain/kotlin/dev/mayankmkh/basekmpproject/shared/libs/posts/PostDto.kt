package dev.mayankmkh.basekmpproject.shared.libs.posts

import kotlinx.serialization.Serializable

/**
 * One post as JSONPlaceholder returns it.
 *
 * `userId` is unused by the screens but kept because dropping a field the server sends is a
 * decision worth making explicitly -- and because it is the one field here that shows the DTO is a
 * wire contract rather than a domain model.
 */
@Serializable
data class PostDto(
    val userId: Int,
    val id: Int,
    val title: String,
    val body: String,
)
