package __PACKAGE__

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class __NAME__ContentTest {
    @Test
    fun `content renders the fixture and reports the tapped action`() = runComposeUiTest {
        val actions = mutableListOf<__NAME__Action>()
        setContent { __NAME__Content(__NAME__Fixtures.state, actions::add) }

        onNodeWithText("__name__-1").assertIsDisplayed()
        onNodeWithText("Select").performClick()

        assertEquals(__NAME__Action.Select, actions.single())
    }
}
