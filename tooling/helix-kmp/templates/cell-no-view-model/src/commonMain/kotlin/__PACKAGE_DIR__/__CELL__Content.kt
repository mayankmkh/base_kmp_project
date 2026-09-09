package __PACKAGE__

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Stateless rendering: values in, callbacks out. Nothing here knows about Koin or navigation. */
@Composable
internal fun __CELL__Content(
    label: String,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label)
        if (isExpanded) {
            Text("Expanded")
        }
        Button(onClick = { onExpandedChange(!isExpanded) }) { Text("Toggle") }
        Button(onClick = onSelect) { Text("Select") }
        Button(onClick = onBack) { Text("Back") }
    }
}
