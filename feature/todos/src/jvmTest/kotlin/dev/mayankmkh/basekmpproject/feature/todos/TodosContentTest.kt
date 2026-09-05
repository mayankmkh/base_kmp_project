package dev.mayankmkh.basekmpproject.feature.todos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoField
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.foundation.resource.Violation
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
