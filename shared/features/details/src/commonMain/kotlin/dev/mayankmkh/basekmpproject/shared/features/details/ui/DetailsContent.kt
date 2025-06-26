package dev.mayankmkh.basekmpproject.shared.features.details.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import base_kmp_project.shared.features.details.generated.resources.Res
import base_kmp_project.shared.features.details.generated.resources.arrow_back_24px
import dev.mayankmkh.basekmpproject.shared.features.details.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.details.nav.DetailsComponent
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.UiState
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsContent(component: DetailsComponent, modifier: Modifier = Modifier) {
    val viewModel = component.viewModel

    val uiState by viewModel.uiStateFlow.collectAsState()

    val title =
        when (val viewState = uiState) {
            is UiState.Success<Item> -> viewState.data.title
            else -> null
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "Detail Screen $title") },
                navigationIcon = {
                    IconButton(onClick = viewModel::onCloseClicked) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = "Close button",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues).fillMaxSize()) {
            when (val viewState = uiState) {
                UiState.Initial -> Initial()
                UiState.InProgress -> InProgress()
                is UiState.Success -> Success(viewState.data)
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
private fun Success(item: Item, modifier: Modifier = Modifier) {
    Text(
        text = item.text,
        modifier =
            modifier.fillMaxWidth().verticalScroll(state = rememberScrollState()).padding(16.dp),
    )
}

@Composable
private fun Failure(throwable: Throwable, modifier: Modifier = Modifier) {
    Text("Error $throwable", modifier)
}
