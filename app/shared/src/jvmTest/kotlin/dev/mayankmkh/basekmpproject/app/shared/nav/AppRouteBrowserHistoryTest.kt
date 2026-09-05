package dev.mayankmkh.basekmpproject.app.shared.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppRouteBrowserHistoryTest {
    @Test
    fun `every app route round trips through its stable fragment`() {
        val routes =
            listOf(
                PostFeedRoute,
                PostDetailRoute(id = 42),
                TodoListRoute,
                TodoDetailRoute(id = 73),
                TodoEditorRoute,
            )

        routes.forEach { route -> assertEquals(route, restoreAppRoute(saveAppRoute(route)!!)) }
    }

    @Test
    fun `existing post fragment names stay stable`() {
        assertEquals("#list", saveAppRoute(PostFeedRoute))
        assertEquals("#details?id=42", saveAppRoute(PostDetailRoute(id = 42)))
    }

    @Test
    fun `invalid fragments do not create routes`() {
        assertNull(restoreAppRoute("#details?id=not-a-number"))
        assertNull(restoreAppRoute("#todo-details"))
        assertNull(restoreAppRoute("#unknown"))
    }
}
