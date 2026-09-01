package dev.mayankmkh.basekmpproject.shared.features.list.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ItemsModel
import dev.mayankmkh.basekmpproject.shared.features.list.presentation.ListViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.UiState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ListScreen(onItemSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ListViewModel = koinViewModel()
    val currentOnItemSelected by rememberUpdatedState(onItemSelected)

    LaunchedEffect(viewModel) {
        viewModel.eventsFlow.collect { event ->
            when (event) {
                is ListViewModel.Event.ItemClicked -> currentOnItemSelected(event.item)
            }
        }
    }

    ListContent(viewModel = viewModel, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListContent(
    viewModel: ListViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiStateFlow.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(text = "Navigation 3 Sample") }) },
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            when (val viewState = uiState) {
                UiState.Initial -> Initial(Modifier.padding(paddingValues))
                UiState.InProgress -> InProgress(Modifier.padding(paddingValues))
                // The list gets the insets as content padding rather than as a margin, so its
                // items scroll under the system bars instead of stopping at them.
                is UiState.Success ->
                    Success(
                        viewState.data,
                        onItemClick = viewModel::onItemClicked,
                        contentPadding = paddingValues,
                    )
                is UiState.Failure -> Failure(viewState.error, Modifier.padding(paddingValues))
            }
        }
    }
}

@Composable private fun Initial(modifier: Modifier = Modifier) = Text("Initializing", modifier)

@Composable
private fun InProgress(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier)
}

@Composable
private fun Success(
    itemsModel: ItemsModel,
    onItemClick: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        items(itemsModel.items) { item ->
            Text(
                text = item.title,
                modifier =
                    Modifier.fillMaxWidth().clickable { onItemClick(item.id) }.padding(16.dp),
            )
        }
    }
}

@Composable
private fun Failure(throwable: Throwable, modifier: Modifier = Modifier) {
    Text("Error $throwable", modifier)
}
