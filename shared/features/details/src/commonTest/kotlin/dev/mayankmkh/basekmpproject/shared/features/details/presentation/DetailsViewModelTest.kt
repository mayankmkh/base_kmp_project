package dev.mayankmkh.basekmpproject.shared.features.details.presentation

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.shared.features.details.testing.FakeDetailRepository
import dev.mayankmkh.basekmpproject.shared.features.details.testing.RecordingFailureListener
import dev.mayankmkh.basekmpproject.shared.features.details.testing.detailsViewModel
import dev.mayankmkh.basekmpproject.shared.features.details.testing.items
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.UiState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class DetailsViewModelTest {
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
    fun `emits progress then the item the id points at`() =
        runTest(dispatcher) {
            val viewModel = detailsViewModel(dispatcher, itemId = "2")

            viewModel.uiStateFlow.test {
                assertEquals(UiState.Initial, awaitItem())
                assertEquals(UiState.InProgress, awaitItem())
                assertEquals(UiState.Success(items[1]), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `surfaces a lookup failure and reports it`() =
        runTest(dispatcher) {
            val error = IllegalStateException("boom")
            val failureListener = RecordingFailureListener()
            val viewModel =
                detailsViewModel(
                    dispatcher,
                    repository = FakeDetailRepository(failure = error),
                    failureListener = failureListener,
                )

            viewModel.uiStateFlow.test {
                assertEquals(UiState.Initial, awaitItem())
                assertEquals(UiState.InProgress, awaitItem())
                // The same instance this time: the flow use case catches the throwable and wraps
                // it in an `Err` without crossing a `withContext` boundary, so nothing rebuilds it.
                assertEquals(UiState.Failure(error), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(error, failureListener.failures.single())
        }

    @Test
    fun `onCloseClicked emits close`() =
        runTest(dispatcher) {
            val viewModel = detailsViewModel(dispatcher)

            viewModel.eventsFlow.test {
                viewModel.onCloseClicked()

                assertEquals(DetailsViewModel.Event.Close, awaitItem())
            }
        }
}
