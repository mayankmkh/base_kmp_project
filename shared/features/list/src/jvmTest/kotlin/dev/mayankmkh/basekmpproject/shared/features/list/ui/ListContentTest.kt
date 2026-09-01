package dev.mayankmkh.basekmpproject.shared.features.list.ui

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.shared.features.list.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.list.presentation.ListViewModel
import dev.mayankmkh.basekmpproject.shared.features.list.testing.FakeListRepository
import dev.mayankmkh.basekmpproject.shared.features.list.testing.items
import dev.mayankmkh.basekmpproject.shared.features.list.testing.listViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalTestApi::class)
class ListContentTest {
    // The view model builds its scope on `Dispatchers.Main.immediate`, which has no implementation
    // off-device until a test one is installed. Unconfined rather than standard: the compose test
    // drives its own clock, so there is no `runTest` scheduler here to hand work back to.
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(dispatcher)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `shows a row for every item`() = runComposeUiTest {
        setContent { ListContent(viewModel(flowOf(Ok(items)))) }

        onNodeWithText("First").assertIsDisplayed()
        onNodeWithText("Second").assertIsDisplayed()
    }

    @Test
    fun `spins while the items are still on their way`() = runComposeUiTest {
        setContent { ListContent(viewModel(flow { awaitCancellation() })) }

        onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    }

    @Test
    fun `shows what went wrong when the items cannot be loaded`() = runComposeUiTest {
        setContent {
            ListContent(viewModel(flowOf(Err(IllegalStateException("boom")))))
        }

        onNodeWithText("boom", substring = true).assertIsDisplayed()
    }

    @Test
    fun `says so when the feed comes back with nothing in it`() = runComposeUiTest {
        setContent { ListContent(viewModel(flowOf(Ok(emptyList())))) }

        // A successful fetch of nothing, not a failure: no error text and nothing to retry.
        onNodeWithText("Nothing here yet.", substring = true).assertIsDisplayed()
        onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun `the retry button asks for another fetch`() = runComposeUiTest {
        val repository = FakeListRepository(flowOf(Err(IllegalStateException("boom"))))
        setContent { ListContent(listViewModel(dispatcher, repository = repository)) }

        onNodeWithText("Retry").performClick()

        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `pulling the list down asks for another fetch`() = runComposeUiTest {
        val repository = FakeListRepository(flowOf(Ok(items)))
        setContent { ListContent(listViewModel(dispatcher, repository = repository)) }

        onNodeWithText("First").performTouchInput {
            // Past the indicator's threshold: a shorter drag settles back without refreshing.
            swipeDown(startY = centerY, endY = centerY + 800f)
        }
        waitForIdle()

        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `tapping a row reports which item was tapped`() = runComposeUiTest {
        val viewModel = listViewModel(dispatcher, flowOf(Ok(items)))
        val events = mutableListOf<ListViewModel.Event>()
        scope.launch { viewModel.eventsFlow.toList(events) }
        setContent { ListContent(viewModel) }

        onNodeWithText("Second").performClick()

        // The id, not the title: the row shows one and reports the other.
        assertEquals(ListViewModel.Event.ItemClicked("2"), events.single())
    }

    private fun viewModel(results: Flow<Result<Collection<Item>, Throwable>>): ListViewModel =
        listViewModel(dispatcher, results)
}
