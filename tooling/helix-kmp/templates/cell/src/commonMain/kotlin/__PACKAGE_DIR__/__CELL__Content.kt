package __PACKAGE__

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Stateless rendering: State in, Actions out. Nothing here knows about Koin or navigation. */
@Composable
internal fun __CELL__Content(
    state: __CELL__State,
    onAction: (__CELL__Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(state.id)
        Button(onClick = { onAction(__CELL__Action.Select) }) { Text("Select") }
        Button(onClick = { onAction(__CELL__Action.Back) }) { Text("Back") }
    }
}
