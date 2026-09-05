package __PACKAGE__

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Stateless rendering: State in, Actions out. Nothing here knows about Koin or navigation. */
@Composable
internal fun __NAME__Content(
    state: __NAME__State,
    onAction: (__NAME__Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (state.isInitialLoading || state.isRefreshing) {
            CircularProgressIndicator()
        }
        Text(state.id)
        Text(state.label.orEmpty())
        // Product code should map the stable kind to a localized string resource.
        state.problem?.let { problem -> Text(problem.kind.name) }
        Button(onClick = { onAction(__NAME__Action.Refresh) }) { Text("Refresh") }
        Button(onClick = { onAction(__NAME__Action.Select) }) { Text("Select") }
    }
}
