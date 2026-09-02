package __PACKAGE__

import app.cash.turbine.test
import __PACKAGE__.api.__CELL__Output
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.testkit.runMainTest
import kotlin.test.Test
import kotlin.test.assertEquals

class __CELL__ViewModelTest {
    @Test
    fun `select raises the selected output for this instance`() = runMainTest {
        val viewModel = __CELL__ViewModel("__cell__-1", instanceKey())

        viewModel.onAction(__CELL__Action.Select)

        viewModel.outputs.test { assertEquals(__CELL__Output.Selected("__cell__-1"), awaitItem()) }
    }

    @Test
    fun `back raises the back output`() = runMainTest {
        val viewModel = __CELL__ViewModel("__cell__-1", instanceKey())

        viewModel.onAction(__CELL__Action.Back)

        viewModel.outputs.test { assertEquals(__CELL__Output.Back, awaitItem()) }
    }

    private fun instanceKey() = FeatureInstanceKey.forScreen("__cell__/1", "__cell__")
}
