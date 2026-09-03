package __PACKAGE__

import app.cash.turbine.test
import __CAP_PACKAGE__.__CAP_NAME__Commands
import __CAP_PACKAGE__.__CAP_NAME__Id
import __CAP_PACKAGE__.__CAP_NAME__Queries
import __CAP_PACKAGE__.__CAP_NAME__Record
import __PACKAGE__.api.__NAME__Output
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import dev.mayankmkh.basekmpproject.testkit.runMainTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// A Feature is tested against the Capability's interfaces, never against its implementation. Move
// this fake into a `:testkit:*` module once more than one Feature needs it.
private class Fake__CAP_NAME__ : __CAP_NAME__Queries, __CAP_NAME__Commands {
    val records = MutableStateFlow(emptyList<__CAP_NAME__Record>())
    var refreshCount: Int = 0
        private set

    var refreshOutcome: RefreshOutcome = RefreshOutcome.Succeeded

    override fun observeAll(): Flow<List<__CAP_NAME__Record>> = records

    override fun observe(id: __CAP_NAME__Id): Flow<__CAP_NAME__Record?> {
        return records.map { current -> current.firstOrNull { it.id == id } }
    }

    override suspend fun refresh(): RefreshOutcome {
        refreshCount++
        return refreshOutcome
    }
}

class __NAME__ViewModelTest {
    @Test
    fun `the observed record becomes the label`() = runMainTest {
        val capability = Fake__CAP_NAME__()
        capability.records.value = listOf(__CAP_NAME__Record(__CAP_NAME__Id("__name__-1"), "Hello"))

        val viewModel = viewModel(capability)

        viewModel.state.test {
            // The initial State is published before the observer started, so there are two items.
            assertEquals("", awaitItem().label)
            assertEquals("Hello", awaitItem().label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh asks the capability to synchronize`() = runMainTest {
        val capability = Fake__CAP_NAME__()
        capability.refreshOutcome =
            RefreshOutcome.Failed(
                ResourceProblem(ResourceProblemCategory.TEMPORARY, retryable = true)
            )
        val viewModel = viewModel(capability)

        viewModel.onAction(__NAME__Action.Refresh)

        viewModel.uiCommands.test { assertIs<__NAME__UiCommand.ShowMessage>(awaitItem()) }
        assertEquals(1, capability.refreshCount)
    }

    @Test
    fun `back raises the back output`() = runMainTest {
        val viewModel = viewModel(Fake__CAP_NAME__())

        viewModel.onAction(__NAME__Action.Back)

        viewModel.outputs.test { assertEquals(__NAME__Output.Back, awaitItem()) }
    }

    private fun viewModel(capability: Fake__CAP_NAME__) =
        __NAME__ViewModel(
            id = "__name__-1",
            instanceKey = FeatureInstanceKey.forScreen("__name__/1", "__name__"),
            queries = capability,
            commands = capability,
        )
}
