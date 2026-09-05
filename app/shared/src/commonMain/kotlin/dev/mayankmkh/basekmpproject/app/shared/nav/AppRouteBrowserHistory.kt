package dev.mayankmkh.basekmpproject.app.shared.nav

import androidx.navigation3.runtime.NavKey

/** Converts an app-owned route to the stable URL fragment used by the web host. */
public fun saveAppRoute(route: NavKey): String? =
    when (route) {
        PostFeedRoute -> "#list"
        is PostDetailRoute -> "#details?id=${route.id}"
        TodoListRoute -> "#todos"
        is TodoDetailRoute -> "#todo-details?id=${route.id}"
        TodoEditorRoute -> "#todo-editor"
        else -> null
    }

/** Restores one typed app route from a web URL fragment. */
public fun restoreAppRoute(fragment: String): NavKey? {
    val value = fragment.substringAfter('#', missingDelimiterValue = "")
    val name = value.substringBefore('?')
    val parameters =
        value
            .substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .mapNotNull { parameter ->
                val parts = parameter.split('=', limit = 2)
                parts.takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }
            .toMap()
    return when (name) {
        "list" -> PostFeedRoute
        "details" -> parameters["id"]?.toLongOrNull()?.let(::PostDetailRoute)
        "todos" -> TodoListRoute
        "todo-details" -> parameters["id"]?.toLongOrNull()?.let(::TodoDetailRoute)
        "todo-editor" -> TodoEditorRoute
        else -> null
    }
}
