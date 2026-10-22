package dev.mayankmkh.basekmpproject.feature.todos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.height
import dev.mayankmkh.basekmpproject.capability.todos.api.Todo
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoField
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.foundation.resource.Violation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TodosContentTest {
    @Test
    fun `list renders rows and reports open toggle and delete actions`() = runComposeUiTest {
        val actions = mutableListOf<TodoListAction>()
        setContent { TodoListContent(TodosFeatureFixtures.list, actions::add, summary = {}) }

        onNodeWithText("Buy groceries").performClick()
        onAllNodesWithText("Delete", useUnmergedTree = true)[0].performClick()

        assertEquals(TodoListAction.Open(TodoId(1)), actions.first())
        assertEquals(TodoListAction.RequestDelete(TodoId(1)), actions.last())
    }

    @Test
    fun `list renders loading empty failure and cached offline fixtures`() = runComposeUiTest {
        var state by mutableStateOf(TodosFeatureFixtures.loadingList)
        setContent { TodoListContent(state, {}, summary = {}) }

        state = TodosFeatureFixtures.emptyList
        waitForIdle()
        onNodeWithText("No todos match these settings.").assertIsDisplayed()

        state = TodosFeatureFixtures.errorList
        waitForIdle()
        onNodeWithText("Retry").assertIsDisplayed()

        state = TodosFeatureFixtures.offlineList
        waitForIdle()
        onNodeWithText("Buy groceries").assertIsDisplayed()
    }

    @Test
    fun `editor shows client and server validation messages verbatim`() = runComposeUiTest {
        var state by mutableStateOf(TodosFeatureFixtures.editorFieldError)
        setContent { TodoEditorContent(state, {}) }
        onNodeWithText("Enter a title").assertIsDisplayed()

        state =
            TodoEditorState(
                violations =
                    listOf(
                        Violation(
                            TodoField.TITLE,
                            "server_code",
                            "Exact server message",
                        )
                    )
            )
        waitForIdle()
        onNodeWithText("Exact server message").assertIsDisplayed()
    }

    /** The create button floats over the list, so the list leaves room below its last row. */
    @Test
    fun `the last row's delete is clear of the create button`() = runComposeUiTest {
        val todos = (1L..30L).map { Todo(TodoId(it), 1, "Todo $it", completed = false) }
        setContent {
            TodoListContent(TodosFeatureFixtures.list.copy(todos = todos), {}, summary = {})
        }

        // The summary and the filter are item 0, so the last todo is item `todos.size`.
        onNode(hasScrollToIndexAction()).performScrollToIndex(todos.size)
        waitForIdle()

        val delete =
            onAllNodesWithText("Delete", useUnmergedTree = true).onLast().getUnclippedBoundsInRoot()
        val create = onNodeWithContentDescription("New todo").getUnclippedBoundsInRoot()
        assertTrue(
            delete.bottom <= create.top,
            "Delete ends at ${delete.bottom}, below ${create.top}",
        )
    }

    @Test
    fun `the create button keeps its height while submitting`() = runComposeUiTest {
        var state by mutableStateOf(TodosFeatureFixtures.editor)
        setContent { TodoEditorContent(state, {}) }
        val idle = onNode(isButton).getUnclippedBoundsInRoot().height

        state = state.copy(isSubmitting = true)
        waitForIdle()

        assertEquals(idle, onNode(isButton).getUnclippedBoundsInRoot().height)
    }

    private companion object {
        val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    }
}
