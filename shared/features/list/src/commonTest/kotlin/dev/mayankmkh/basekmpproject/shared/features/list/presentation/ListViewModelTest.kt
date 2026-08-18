package dev.mayankmkh.basekmpproject.shared.features.list.presentation

import app.cash.turbine.test
import com.github.michaelbull.result.Err
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ItemsModel
import dev.mayankmkh.basekmpproject.shared.features.list.testing.RecordingFailureListener
import dev.mayankmkh.basekmpproject.shared.features.list.testing.items
import dev.mayankmkh.basekmpproject.shared.features.list.testing.listViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.UiState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class ListViewModelTest {
    // The view model builds its scope on `Dispatchers.Main.immediate`, which has no implementation
    // off-device until a test one is installed.
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `stays initial until something collects`() =
        runTest(dispatcher) {
            val viewModel = listViewModel(dispatcher)

            assertEquals(UiState.Initial, viewModel.uiStateFlow.value)
        }

    @Test
    fun `emits progress then the items the repository returns`() =
        runTest(dispatcher) {
            val viewModel = listViewModel(dispatcher)

            viewModel.uiStateFlow.test {
                assertEquals(UiState.Initial, awaitItem())
                assertEquals(UiState.InProgress, awaitItem())
                assertEquals(UiState.Success(ItemsModel(items)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `surfaces a repository failure and reports it`() =
        runTest(dispatcher) {
            val error = IllegalStateException("boom")
            val failureListener = RecordingFailureListener()
            val viewModel = listViewModel(dispatcher, flowOf(Err(error)), failureListener)

            viewModel.uiStateFlow.test {
                assertEquals(UiState.Initial, awaitItem())
                assertEquals(UiState.InProgress, awaitItem())
                assertEquals(UiState.Failure(error), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(error, failureListener.failures.single())
        }

    @Test
    fun `onItemClicked emits the clicked item`() =
        runTest(dispatcher) {
            val viewModel = listViewModel(dispatcher)

            viewModel.eventsFlow.test {
                viewModel.onItemClicked("2")

                assertEquals(ListViewModel.Event.ItemClicked("2"), awaitItem())
            }
        }
}
