package dev.mayankmkh.basekmpproject.shared.features.details.ui

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.mayankmkh.basekmpproject.shared.features.details.domain.DetailRepository
import dev.mayankmkh.basekmpproject.shared.features.details.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.details.nav.DetailsComponent
import dev.mayankmkh.basekmpproject.shared.features.details.presentation.DetailsViewModel
import dev.mayankmkh.basekmpproject.shared.features.details.testing.FakeDetailRepository
import dev.mayankmkh.basekmpproject.shared.features.details.testing.detailsViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalTestApi::class)
class DetailsContentTest {
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
    fun `puts the item's title in the bar and its text below`() = runComposeUiTest {
        setContent { DetailsContent(component(FakeDetailRepository())) }

        onNodeWithText("Detail Screen First").assertIsDisplayed()
        onNodeWithText("First body").assertIsDisplayed()
    }

    @Test
    fun `spins while the item is still on its way`() = runComposeUiTest {
        setContent { DetailsContent(component(NeverAnswers)) }

        onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    }

    @Test
    fun `shows what went wrong when the item cannot be loaded`() = runComposeUiTest {
        val repository = FakeDetailRepository(failure = IllegalStateException("boom"))

        setContent { DetailsContent(component(repository)) }

        onNodeWithText("boom", substring = true).assertIsDisplayed()
    }

    @Test
    fun `tapping the back button asks to close the screen`() = runComposeUiTest {
        val viewModel = detailsViewModel(dispatcher)
        val events = mutableListOf<DetailsViewModel.Event>()
        scope.launch { viewModel.eventsFlow.toList(events) }
        setContent { DetailsContent(component(viewModel)) }

        onNodeWithContentDescription("Close button").performClick()

        assertEquals(DetailsViewModel.Event.Close, events.single())
    }

    private fun component(repository: DetailRepository) =
        component(detailsViewModel(dispatcher, repository = repository))

    private fun component(viewModel: DetailsViewModel) =
        object : DetailsComponent() {
            override val viewModel = viewModel

            override fun processEvent(event: DetailsViewModel.Event) = Unit
        }

    /** Stands in for a fetch that is still in flight, so the screen stays on its loading branch. */
    private object NeverAnswers : DetailRepository {
        override suspend fun getItem(id: String): Item = awaitCancellation()
    }
}
