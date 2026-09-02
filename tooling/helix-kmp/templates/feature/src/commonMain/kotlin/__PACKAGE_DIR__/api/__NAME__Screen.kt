package __PACKAGE__.api

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey

/** Navigable destination: it owns chrome and hosts its Cell, never the Cell's state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun __NAME__Screen(
    id: String,
    instanceKey: FeatureInstanceKey,
    onOutput: (__NAME__Output) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("__NAME__") },
                navigationIcon = {
                    TextButton(onClick = { onOutput(__NAME__Output.Back) }) { Text("Back") }
                },
            )
        },
    ) { padding ->
        __NAME__Cell(
            id = id,
            instanceKey = instanceKey,
            onOutput = onOutput,
            contentPadding = padding,
        )
    }
}
