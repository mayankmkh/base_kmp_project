package dev.mayankmkh.basekmpproject.shared.features.list.ui

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.shared.features.list.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.list.nav.ListComponent
import dev.mayankmkh.basekmpproject.shared.features.list.presentation.ListViewModel
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
        setContent { ListContent(component(flowOf(Ok(items)))) }

        onNodeWithText("First").assertIsDisplayed()
        onNodeWithText("Second").assertIsDisplayed()
    }

    @Test
    fun `spins while the items are still on their way`() = runComposeUiTest {
        setContent { ListContent(component(flow { awaitCancellation() })) }

        onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    }

    @Test
    fun `shows what went wrong when the items cannot be loaded`() = runComposeUiTest {
        setContent { ListContent(component(flowOf(Err(IllegalStateException("boom"))))) }

        onNodeWithText("boom", substring = true).assertIsDisplayed()
    }

    @Test
    fun `tapping a row reports which item was tapped`() = runComposeUiTest {
        val viewModel = listViewModel(dispatcher, flowOf(Ok(items)))
        val events = mutableListOf<ListViewModel.Event>()
        scope.launch { viewModel.eventsFlow.toList(events) }
        setContent { ListContent(component(viewModel)) }

        onNodeWithText("Second").performClick()

        // The id, not the title: the row shows one and reports the other.
        assertEquals(ListViewModel.Event.ItemClicked("2"), events.single())
    }

    private fun component(results: Flow<Result<Collection<Item>, Throwable>>) =
        component(listViewModel(dispatcher, results))

    private fun component(viewModel: ListViewModel) =
        object : ListComponent() {
            override val viewModel = viewModel

            override fun processEvent(event: ListViewModel.Event) = Unit
        }
}
