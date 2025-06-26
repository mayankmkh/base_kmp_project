package dev.mayankmkh.basekmpproject.shared.features.list.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ItemsModel
import dev.mayankmkh.basekmpproject.shared.features.list.nav.ListComponent
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListContent(component: ListComponent, modifier: Modifier = Modifier) {
    val viewModel = component.viewModel

    val uiState by viewModel.uiStateFlow.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(text = "Decompose-Dagger Sample") }) },
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues).fillMaxSize()) {
            when (val viewState = uiState) {
                UiState.Initial -> Initial()
                UiState.InProgress -> InProgress()
                is UiState.Success ->
                    Success(viewState.data, onItemClick = viewModel::onItemClicked)
                is UiState.Failure -> Failure(viewState.error)
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
) {
    LazyColumn(modifier = modifier) {
        items(itemsModel.items) { item ->
            Text(
                text = item.title,
                modifier = Modifier.fillMaxWidth().clickable { onItemClick(item.id) }.padding(16.dp),
            )
        }
    }
}

@Composable
private fun Failure(throwable: Throwable, modifier: Modifier = Modifier) {
    Text("Error $throwable", modifier)
}
