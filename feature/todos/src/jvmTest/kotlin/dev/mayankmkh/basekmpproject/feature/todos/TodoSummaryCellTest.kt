package dev.mayankmkh.basekmpproject.feature.todos

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosCommands
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosQueries
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoSummaryCell
import dev.mayankmkh.basekmpproject.feature.todos.api.todosFeatureModule
import dev.mayankmkh.basekmpproject.foundation.presentation.CellPlacementId
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
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
class TodoSummaryCellTest {
    private val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())
    private val queries = FakeTodosQueries()

    @BeforeTest
    fun setUp() {
        mainDispatcherRule.starting()
        startKoin {
            modules(
                todosFeatureModule,
                module {
                    single<TodosQueries> { queries }
                    single<TodosCommands> { FakeTodosCommands() }
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
    fun `two distinct placement keys own two summary view models`() = runComposeUiTest {
        setContent {
            Column {
                TodoSummaryCell(summaryKey("primary"), {})
                TodoSummaryCell(summaryKey("secondary"), {})
            }
        }

        onAllNodesWithText("1 open, 1 completed").assertCountEquals(2)
        waitUntil(timeoutMillis = 5_000) { queries.listObserverCount == 2 }
        assertEquals(2, queries.listObserverCount)
    }

    private fun summaryKey(placement: String) =
        FeatureInstanceKey.forPlacement(
            surface = "preview/todos",
            cellType = "todo-summary",
            placement = CellPlacementId.fromHostStableId(placement),
        )
}
