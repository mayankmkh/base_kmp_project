package dev.mayankmkh.basekmpproject.shared.features.list.presentation

import app.cash.turbine.test
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ItemsModel
import dev.mayankmkh.basekmpproject.shared.features.list.testing.FakeListRepository
import dev.mayankmkh.basekmpproject.shared.features.list.testing.RecordingFailureListener
import dev.mayankmkh.basekmpproject.shared.features.list.testing.items
import dev.mayankmkh.basekmpproject.shared.features.list.testing.listViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.UiState
import dev.mayankmkh.basekmpproject.shared.libs.connectivity.ConnectivityMonitor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    fun `onRefresh asks the repository to refresh and reports when it is done`() =
        runTest(dispatcher) {
            val repository = FakeListRepository(flowOf(Ok(items)))
            val viewModel = listViewModel(dispatcher, repository = repository)

            viewModel.isRefreshing.test {
                assertEquals(false, awaitItem())

                viewModel.onRefresh()

                assertEquals(true, awaitItem())
                assertEquals(false, awaitItem())
            }
            assertEquals(1, repository.refreshCount)
        }

    @Test
    fun `a second pull while one is running is ignored`() =
        runTest(dispatcher) {
            val repository = FakeListRepository(flowOf(Ok(items)))
            val viewModel = listViewModel(dispatcher, repository = repository)

            viewModel.onRefresh()
            // Nothing has run the first refresh yet -- the standard dispatcher only hands work
            // back at a suspension point -- so this is the case the guard exists for.
            viewModel.onRefresh()
            runCurrent()

            assertEquals(1, repository.refreshCount)
        }

    @Test
    fun `a failed refresh is announced without disturbing the list`() =
        runTest(dispatcher) {
            val error = IllegalStateException("offline")
            val repository = FakeListRepository(flowOf(Ok(items)), refreshFailure = error)
            val viewModel = listViewModel(dispatcher, repository = repository)
            val events = mutableListOf<ListViewModel.Event>()
            backgroundScope.launch { viewModel.eventsFlow.toList(events) }

            viewModel.uiStateFlow.test {
                assertEquals(UiState.Initial, awaitItem())
                assertEquals(UiState.InProgress, awaitItem())
                assertEquals(UiState.Success(ItemsModel(items)), awaitItem())

                viewModel.onRefresh()
                runCurrent()

                // Still showing the cached items: a refresh failure is announced, not folded into
                // the state, so the list the user was reading stays put.
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            val failed = assertIs<ListViewModel.Event.RefreshFailed>(events.single())
            assertEquals("offline", failed.error.message)
        }

    @Test
    fun `coming back online revalidates the cache`() =
        runTest(dispatcher) {
            val online = MutableStateFlow(false)
            val repository = FakeListRepository(flowOf(Ok(items)))
            listViewModel(
                dispatcher,
                repository = repository,
                connectivityMonitor = ConnectivityMonitor { online },
            )
            runCurrent()
            assertEquals(0, repository.refreshCount)

            online.value = true
            runCurrent()

            assertEquals(1, repository.refreshCount)
        }

    @Test
    fun `being online to begin with is not a reconnection`() =
        runTest(dispatcher) {
            val repository = FakeListRepository(flowOf(Ok(items)))
            listViewModel(
                dispatcher,
                repository = repository,
                connectivityMonitor = ConnectivityMonitor { MutableStateFlow(true) },
            )
            runCurrent()

            // The screen fetches on its own when it opens. A refresh here would be a second fetch
            // of the same feed, a step behind the first.
            assertEquals(0, repository.refreshCount)
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
