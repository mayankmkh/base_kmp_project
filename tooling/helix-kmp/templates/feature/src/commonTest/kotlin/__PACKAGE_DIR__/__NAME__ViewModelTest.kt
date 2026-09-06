package __PACKAGE__

import app.cash.turbine.test
import __PACKAGE__.api.__NAME__Output
import dev.mayankmkh.basekmpproject.testkit.runMainTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class __NAME__ViewModelTest {
    @Test
    fun `select raises an output and refresh raises a ui command`() = runMainTest {
        val viewModel = __NAME__ViewModel("__name__-1")

        viewModel.state.test {
            assertEquals("__name__-1", awaitItem().id)

            viewModel.onAction(__NAME__Action.Select)
            viewModel.outputs.test {
                assertEquals(__NAME__Output.Selected("__name__-1"), awaitItem())
            }

            viewModel.onAction(__NAME__Action.Refresh)
            viewModel.uiCommands.test { assertIs<__NAME__UiCommand.ShowMessage>(awaitItem()) }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `back raises the back output`() = runMainTest {
        val viewModel = __NAME__ViewModel("__name__-1")

        viewModel.onAction(__NAME__Action.Back)

        viewModel.outputs.test { assertEquals(__NAME__Output.Back, awaitItem()) }
    }
}
