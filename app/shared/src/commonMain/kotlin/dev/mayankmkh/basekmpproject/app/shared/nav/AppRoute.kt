package dev.mayankmkh.basekmpproject.app.shared.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Routes owned by the Posts destination; the marker is what assigns a route to its back stack. */
sealed interface PostsRoute : NavKey {
    /**
     * The presentation surface this route hosts on: the stable prefix of every `FeatureInstanceKey`
     * built for it.
     *
     * It is deliberately not [saveAppRoute]: that form is a browser URL fragment with `#` and query
     * syntax, while this one is a key segment path. Changing either string is a saved-state break,
     * so they are kept apart rather than derived from one another.
     */
    val instanceSurface: String
}

/** Routes owned by the Todos destination. */
sealed interface TodosRoute : NavKey {
    /** The presentation surface this route hosts on. See [PostsRoute.instanceSurface]. */
    val instanceSurface: String
}

@Serializable
data object PostFeedRoute : PostsRoute {
    override val instanceSurface: String
        get() = "posts/feed"
}

@Serializable
data class PostDetailRoute(val id: Long) : PostsRoute {
    override val instanceSurface: String
        get() = "posts/detail/$id"
}

@Serializable
data object TodoListRoute : TodosRoute {
    override val instanceSurface: String
        get() = "todos/list"
}

@Serializable
data class TodoDetailRoute(val id: Long) : TodosRoute {
    override val instanceSurface: String
        get() = "todos/detail/$id"
}

@Serializable
data object TodoEditorRoute : TodosRoute {
    override val instanceSurface: String
        get() = "todos/editor"
}
