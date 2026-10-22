package dev.mayankmkh.basekmpproject.feature.todos

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosCommands
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosQueries
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoEditorOutput
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoEditorScreen
import dev.mayankmkh.basekmpproject.feature.todos.api.todosFeatureModule
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.Problem
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind
import dev.mayankmkh.basekmpproject.testkit.FakeTodosCommands
import dev.mayankmkh.basekmpproject.testkit.FakeTodosQueries
import dev.mayankmkh.basekmpproject.testkit.MainDispatcherRule
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class TodoEditorScreenTest {
    private val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())
    private val commands =
        FakeTodosCommands().apply { onCreate = { Outcome.Failed(Problem(ProblemKind.UNEXPECTED)) } }

    @BeforeTest
    fun setUp() {
        mainDispatcherRule.starting()
        startKoin {
            modules(
                todosFeatureModule,
                module {
                    single<TodosQueries> { FakeTodosQueries() }
                    single<TodosCommands> { commands }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        mainDispatcherRule.finished()
    }

    @Test
    fun `a failed create stays on the editor and shows the failure in a snackbar`() =
        runComposeUiTest {
            val outputs = mutableListOf<TodoEditorOutput>()
            setContent {
                TodoEditorScreen(
                    instanceKey = FeatureInstanceKey.forScreen("preview/todos", "todo-editor"),
                    onOutput = outputs::add,
                )
            }

            onNodeWithText("Title").performTextInput("Ship Todos")
            onNodeWithText("Create").performClick()

            waitUntilAtLeastOneExists(hasText("Something went wrong"), timeoutMillis = 5_000)
            onNodeWithText("Something went wrong").assertIsDisplayed()
            onNodeWithText("Create").assertIsDisplayed()
            assertEquals(emptyList(), outputs)
        }
}
