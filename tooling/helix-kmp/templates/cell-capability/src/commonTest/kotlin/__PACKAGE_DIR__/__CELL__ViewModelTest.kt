package __PACKAGE__

import app.cash.turbine.test
import __CAP_PACKAGE__.__CAP_NAME__Id
import __CAP_PACKAGE__.__CAP_NAME__Queries
import __CAP_PACKAGE__.__CAP_NAME__Record
import __PACKAGE__.api.__CELL__Output
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.testkit.ResourceObservationFixtures
import dev.mayankmkh.basekmpproject.testkit.runMainTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

private class Fake__CAP_NAME__Queries : __CAP_NAME__Queries {
    private val records =
        MutableStateFlow<ResourceObservation<List<__CAP_NAME__Record>>>(ResourceObservation.initial())

    override fun observeAll(): Flow<ResourceObservation<List<__CAP_NAME__Record>>> = records

    override fun observe(id: __CAP_NAME__Id): Flow<ResourceObservation<__CAP_NAME__Record>> =
        MutableStateFlow(ResourceObservationFixtures.idle(__CAP_NAME__Record(id, id.value)))
}

class __CELL__ViewModelTest {
    @Test
    fun `select raises the selected output for this capability id`() = runMainTest {
        val id = __CAP_NAME__Id("__cell__-1")
        val viewModel = __CELL__ViewModel(id, Fake__CAP_NAME__Queries())

        viewModel.onAction(__CELL__Action.Select)

        viewModel.outputs.test { assertEquals(__CELL__Output.Selected(id), awaitItem()) }
    }

    @Test
    fun `back raises the back output`() = runMainTest {
        val viewModel = __CELL__ViewModel(__CAP_NAME__Id("__cell__-1"), Fake__CAP_NAME__Queries())

        viewModel.onAction(__CELL__Action.Back)

        viewModel.outputs.test { assertEquals(__CELL__Output.Back, awaitItem()) }
    }
}
