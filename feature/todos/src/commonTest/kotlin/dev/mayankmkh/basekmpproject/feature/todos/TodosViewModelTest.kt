package dev.mayankmkh.basekmpproject.feature.todos

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.capability.todos.api.CreateTodoResult
import dev.mayankmkh.basekmpproject.capability.todos.api.DeleteTodoResult
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoField
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSettings
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSort
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoViolation
import dev.mayankmkh.basekmpproject.capability.todos.api.UpdateTodoResult
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoDetailOutput
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoEditorOutput
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoListOutput
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import dev.mayankmkh.basekmpproject.testkit.FakeTodosCommands
import dev.mayankmkh.basekmpproject.testkit.FakeTodosQueries
import dev.mayankmkh.basekmpproject.testkit.runMainTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.test.runCurrent

class TodosViewModelTest {
    private val problem = ResourceProblem(ResourceProblemCategory.OFFLINE, retryable = true)
    private val invalid = listOf(TodoViolation(TodoField.TITLE, "blank", message = null))

    @Test
    fun `list maps observations opens routes and updates settings`() = runMainTest {
        val queries = FakeTodosQueries()
        val commands = FakeTodosCommands()
        val viewModel = TodoListViewModel(listKey(), queries, commands)

        viewModel.state.test {
            awaitItem()
            assertEquals(TodosFeatureFixtures.todos, awaitItem().todos)
            viewModel.onAction(TodoListAction.Open(TodoId(2)))
            viewModel.outputs.test {
                assertEquals(TodoListOutput.OpenDetail(TodoId(2)), awaitItem())
            }
            viewModel.onAction(TodoListAction.SetSort(TodoSort.TITLE))
            runCurrent()
            assertEquals(TodoSettings(sort = TodoSort.TITLE), commands.settingsUpdates.single())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `list refresh and failed mutations emit transient failures`() = runMainTest {
        val commands =
            FakeTodosCommands().apply {
                onRefreshTodos = { RefreshOutcome.Failed(problem) }
                onSetCompleted = { _, _ -> UpdateTodoResult.Failed(problem) }
                onDelete = { DeleteTodoResult.Failed(problem) }
            }
        val viewModel = TodoListViewModel(listKey(), FakeTodosQueries(), commands)

        viewModel.uiCommands.test {
            viewModel.onAction(TodoListAction.Refresh)
            assertEquals(
                ResourceProblemCategory.OFFLINE,
                assertIs<TodosUiCommand.ShowFailure>(awaitItem()).category,
            )
            viewModel.onAction(TodoListAction.SetCompleted(TodoId(1), true))
            assertIs<TodosUiCommand.ShowFailure>(awaitItem())
            viewModel.onAction(TodoListAction.RequestDelete(TodoId(1)))
            viewModel.onAction(TodoListAction.ConfirmDelete)
            assertIs<TodosUiCommand.ShowFailure>(awaitItem())
        }
    }

    @Test
    fun `list mutation invalid input and not found surface as transient failures`() = runMainTest {
        val commands =
            FakeTodosCommands().apply {
                onSetCompleted = { _, _ -> UpdateTodoResult.InvalidInput(invalid) }
                onDelete = { DeleteTodoResult.NotFound }
            }
        val viewModel = TodoListViewModel(listKey(), FakeTodosQueries(), commands)
        val permanent = TodosUiCommand.ShowFailure(ResourceProblemCategory.PERMANENT)

        viewModel.uiCommands.test {
            viewModel.onAction(TodoListAction.SetCompleted(TodoId(1), true))
            assertEquals(permanent, awaitItem())
            viewModel.onAction(TodoListAction.RequestDelete(TodoId(1)))
            viewModel.onAction(TodoListAction.ConfirmDelete)
            assertEquals(permanent, awaitItem())
        }
    }

    @Test
    fun `successful list update and delete invoke their commands`() = runMainTest {
        val commands = FakeTodosCommands()
        val viewModel = TodoListViewModel(listKey(), FakeTodosQueries(), commands)

        viewModel.onAction(TodoListAction.SetCompleted(TodoId(1), true))
        runCurrent()
        viewModel.onAction(TodoListAction.RequestDelete(TodoId(2)))
        viewModel.onAction(TodoListAction.ConfirmDelete)
        runCurrent()

        assertEquals(listOf(TodoId(1) to true), commands.completedChanges)
        assertEquals(listOf(TodoId(2)), commands.deletes)
    }

    @Test
    fun `detail covers updated invalid failed and not found results`() = runMainTest {
        val commands = FakeTodosCommands()
        val viewModel = TodoDetailViewModel(TodoId(1), detailKey(), FakeTodosQueries(), commands)

        viewModel.state.test {
            awaitItem()
            awaitItem()
            viewModel.onAction(TodoDetailAction.TitleChanged("Renamed"))
            viewModel.onAction(TodoDetailAction.Rename)
            runCurrent()
            assertEquals(TodoId(1) to "Renamed", commands.renames.single())

            commands.onRename = { _, _ -> UpdateTodoResult.InvalidInput(invalid) }
            viewModel.onAction(TodoDetailAction.Rename)
            runCurrent()
            assertEquals(invalid, viewModel.state.value.violations)

            commands.onRename = { _, _ -> UpdateTodoResult.Failed(problem) }
            viewModel.uiCommands.test {
                viewModel.onAction(TodoDetailAction.Rename)
                assertIs<TodosUiCommand.ShowFailure>(awaitItem())
            }

            commands.onRename = { _, _ -> UpdateTodoResult.NotFound }
            viewModel.outputs.test {
                viewModel.onAction(TodoDetailAction.Rename)
                assertEquals(TodoDetailOutput.NotFound(TodoId(1)), awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `detail delete covers deleted failed and not found results`() = runMainTest {
        val commands = FakeTodosCommands()
        val viewModel = TodoDetailViewModel(TodoId(1), detailKey(), FakeTodosQueries(), commands)

        viewModel.outputs.test {
            viewModel.onAction(TodoDetailAction.ConfirmDelete)
            assertEquals(TodoDetailOutput.Deleted(TodoId(1)), awaitItem())
        }

        commands.onDelete = { DeleteTodoResult.Failed(problem) }
        viewModel.uiCommands.test {
            viewModel.onAction(TodoDetailAction.ConfirmDelete)
            assertIs<TodosUiCommand.ShowFailure>(awaitItem())
        }

        commands.onDelete = { DeleteTodoResult.NotFound }
        viewModel.outputs.test {
            viewModel.onAction(TodoDetailAction.ConfirmDelete)
            assertEquals(TodoDetailOutput.NotFound(TodoId(1)), awaitItem())
        }
    }

    @Test
    fun `detail refresh reports only the failed outcome`() = runMainTest {
        val commands = FakeTodosCommands()
        val viewModel = TodoDetailViewModel(TodoId(1), detailKey(), FakeTodosQueries(), commands)

        viewModel.uiCommands.test {
            viewModel.onAction(TodoDetailAction.Refresh)
            runCurrent()
            expectNoEvents()

            commands.onRefreshTodo = { _, _ -> RefreshOutcome.Failed(problem) }
            viewModel.onAction(TodoDetailAction.Refresh)
            assertIs<TodosUiCommand.ShowFailure>(awaitItem())
        }
    }

    @Test
    fun `confirmed missing detail emits not found output`() = runMainTest {
        val queries = FakeTodosQueries()
        val viewModel = TodoDetailViewModel(TodoId(99), detailKey(), queries, FakeTodosCommands())

        // The resource is observed only while the screen collects state, as the real Screen does.
        viewModel.state.test {
            viewModel.outputs.test {
                queries.todoFlows.getValue(TodoId(99)).value =
                    ResourceObservation(
                        value = null,
                        operation =
                            ResourceOperation.Failed(
                                ResourceProblem(
                                    ResourceProblemCategory.PERMANENT,
                                    retryable = false,
                                )
                            ),
                    )
                assertEquals(TodoDetailOutput.NotFound(TodoId(99)), awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editor covers created invalid and failed results`() = runMainTest {
        val commands = FakeTodosCommands()
        val viewModel = TodoEditorViewModel(editorKey(), commands)
        viewModel.onAction(TodoEditorAction.TitleChanged("Ship Todos"))

        viewModel.outputs.test {
            viewModel.onAction(TodoEditorAction.Submit)
            assertEquals(TodoEditorOutput.Created(TodoId(1_000_000)), awaitItem())
        }

        commands.onCreate = { CreateTodoResult.InvalidInput(invalid) }
        viewModel.onAction(TodoEditorAction.Submit)
        runCurrent()
        assertEquals(invalid, viewModel.state.value.violations)
        assertFalse(viewModel.state.value.isSubmitting)

        commands.onCreate = { CreateTodoResult.Failed(problem) }
        viewModel.uiCommands.test {
            viewModel.onAction(TodoEditorAction.Submit)
            assertIs<TodosUiCommand.ShowFailure>(awaitItem())
        }
    }

    private fun listKey() = FeatureInstanceKey.forScreen("todos/list", "todo-list")

    private fun detailKey() = FeatureInstanceKey.forScreen("todos/detail/1", "todo-detail")

    private fun editorKey() = FeatureInstanceKey.forScreen("todos/editor", "todo-editor")
}
