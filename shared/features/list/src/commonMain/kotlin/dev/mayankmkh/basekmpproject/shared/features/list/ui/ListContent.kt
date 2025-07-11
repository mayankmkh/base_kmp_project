package dev.mayankmkh.basekmpproject.shared.features.list.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import dev.mayankmkh.basekmpproject.shared.features.list.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ItemsModel
import dev.mayankmkh.basekmpproject.shared.features.list.nav.ListComponent
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.UiState
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

data class ListUIState(
    val showItems: Boolean = false,
    val animationTrigger: Int = 0,
    val selectedAnimation: AnimationType = AnimationType.FLIP,
    val showAnimationSelector: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListContent(component: ListComponent, modifier: Modifier = Modifier) {
    val viewModel = component.viewModel
    val uiState by viewModel.uiStateFlow.collectAsState()

    // Consolidated UI state
    var listUIState by remember { mutableStateOf(ListUIState()) }

    // Handle state changes
    LaunchedEffect(uiState) {
        if (uiState is UiState.Success) {
            delay(ListAnimationConfig.INITIAL_DELAY.toLong())
            listUIState = listUIState.copy(showItems = true)
            delay(ListAnimationConfig.ITEMS_SHOW_DELAY.toLong())
            listUIState = listUIState.copy(animationTrigger = listUIState.animationTrigger + 1)
        } else {
            listUIState = listUIState.copy(showItems = false)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Beautiful Items",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    AnimationSelectorButton(
                        selectedAnimation = listUIState.selectedAnimation,
                        onClick = {
                            listUIState =
                                listUIState.copy(
                                    showAnimationSelector = !listUIState.showAnimationSelector
                                )
                        },
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = uiState is UiState.Failure,
                enter =
                    fadeIn(animationSpec = tween(ListAnimationConfig.SLIDE_DURATION)) +
                        slideInVertically(
                            animationSpec = tween(ListAnimationConfig.SLIDE_DURATION),
                            initialOffsetY = { it },
                        ),
                exit =
                    fadeOut(animationSpec = tween(ListAnimationConfig.FADE_DURATION)) +
                        slideOutVertically(
                            animationSpec = tween(ListAnimationConfig.FADE_DURATION),
                            targetOffsetY = { it },
                        ),
            ) {
                FloatingActionButton(
                    onClick = { viewModel.onItemClicked("refresh") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier.padding(paddingValues)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                )
                        )
                    )
        ) {
            // Background overlay when selector is open
            AnimatedVisibility(
                visible = listUIState.showAnimationSelector,
                enter =
                    fadeIn(animationSpec = tween(ListAnimationConfig.ANIMATION_SELECTOR_DURATION)),
                exit =
                    fadeOut(animationSpec = tween(ListAnimationConfig.ANIMATION_SELECTOR_DURATION)),
                modifier =
                    Modifier.fillMaxSize()
                        .zIndex(ListUIConfig.BACKGROUND_OVERLAY_Z_INDEX)
                        .clickable { listUIState = listUIState.copy(showAnimationSelector = false) },
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = ListUIConfig.OVERLAY_ALPHA))
                )
            }

            // Animation selector dropdown
            AnimatedVisibility(
                visible = listUIState.showAnimationSelector,
                enter =
                    fadeIn(animationSpec = tween(ListAnimationConfig.ANIMATION_SELECTOR_DURATION)) +
                        slideInVertically(
                            animationSpec = tween(ListAnimationConfig.ANIMATION_SELECTOR_DURATION),
                            initialOffsetY = { -it },
                        ),
                exit =
                    fadeOut(
                        animationSpec = tween(ListAnimationConfig.ANIMATION_SELECTOR_DURATION)
                    ) +
                        slideOutVertically(
                            animationSpec = tween(ListAnimationConfig.ANIMATION_SELECTOR_DURATION),
                            targetOffsetY = { -it },
                        ),
                modifier =
                    Modifier.align(Alignment.TopEnd).zIndex(ListUIConfig.ANIMATION_SELECTOR_Z_INDEX),
            ) {
                AnimationSelector(
                    selectedAnimation = listUIState.selectedAnimation,
                    onAnimationSelected = {
                        listUIState =
                            listUIState.copy(
                                selectedAnimation = it,
                                showAnimationSelector = false,
                                animationTrigger = listUIState.animationTrigger + 1,
                            )
                    },
                )
            }

            AnimatedVisibility(
                visible = true,
                enter =
                    fadeIn(animationSpec = tween(ListAnimationConfig.SLIDE_DURATION)) +
                        slideInVertically(
                            animationSpec = tween(ListAnimationConfig.SLIDE_DURATION),
                            initialOffsetY = { it / 2 },
                        ),
                exit =
                    fadeOut(animationSpec = tween(ListAnimationConfig.FADE_DURATION)) +
                        slideOutVertically(
                            animationSpec = tween(ListAnimationConfig.FADE_DURATION),
                            targetOffsetY = { it / 2 },
                        ),
            ) {
                when (val viewState = uiState) {
                    UiState.Initial -> InitialState()
                    UiState.InProgress -> InProgressState()
                    is UiState.Success ->
                        SuccessState(
                            itemsModel = viewState.data,
                            onItemClick = viewModel::onItemClicked,
                            showItems = listUIState.showItems,
                            animationTrigger = listUIState.animationTrigger,
                            selectedAnimation = listUIState.selectedAnimation,
                        )
                    is UiState.Failure -> FailureState(viewState.error)
                }
            }
        }
    }
}

@Preview
@Composable
private fun SuccessPreview() {
    val itemsModel =
        ItemsModel(
            items =
                Array(
                        100,
                        init = { index ->
                            Item(
                                id = "${index + 1}",
                                title = "Beautiful Item ${index + 1}",
                                text =
                                    "This is a wonderful description for item ${index + 1} that showcases the beautiful design of our app.",
                            )
                        },
                    )
                    .toList()
        )
    SuccessState(
        itemsModel = itemsModel,
        onItemClick = {},
        showItems = true,
        animationTrigger = 1,
        selectedAnimation = AnimationType.FLIP,
    )
}
