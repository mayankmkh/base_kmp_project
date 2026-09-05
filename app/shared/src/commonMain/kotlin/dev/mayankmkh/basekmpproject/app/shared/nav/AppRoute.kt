package dev.mayankmkh.basekmpproject.app.shared.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Routes owned by the Posts destination; the marker is what assigns a route to its back stack. */
sealed interface PostsRoute : NavKey

/** Routes owned by the Todos destination. */
sealed interface TodosRoute : NavKey

@Serializable data object PostFeedRoute : PostsRoute

@Serializable data class PostDetailRoute(val id: Long) : PostsRoute

@Serializable data object TodoListRoute : TodosRoute

@Serializable data class TodoDetailRoute(val id: Long) : TodosRoute

@Serializable data object TodoEditorRoute : TodosRoute
