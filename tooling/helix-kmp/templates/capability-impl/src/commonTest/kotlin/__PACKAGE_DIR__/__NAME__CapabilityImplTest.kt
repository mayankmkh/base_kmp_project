package __PACKAGE__

import app.cash.turbine.test
import __API_PACKAGE__.__NAME__Id
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class __NAME__CapabilityImplTest {
    @Test
    fun `refresh publishes records to existing observers`() = runTest {
        val capability = __NAME__CapabilityImpl()

        capability.observeAll().test {
            assertEquals(emptyList(), awaitItem())
            capability.refresh()
            assertEquals(listOf(__NAME__Id("1")), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observing an unknown id emits null`() = runTest {
        val capability = __NAME__CapabilityImpl()

        capability.observe(__NAME__Id("missing")).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
