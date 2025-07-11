package dev.mayankmkh.basekmpproject.shared.features.list.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ItemsModel

@Composable
internal fun InitialState(modifier: Modifier = Modifier) {
    val scale = usePulseAnimation()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(ListUIConfig.INITIAL_INDICATOR_SIZE.dp).scale(scale),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(ListUIConfig.CONTENT_SPACER_HEIGHT.dp))
            Text(
                text = "Initializing...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun InProgressState(modifier: Modifier = Modifier) {
    val rotation = useRotationAnimation()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier =
                    Modifier.size(ListUIConfig.LOADING_INDICATOR_SIZE.dp)
                        .graphicsLayer(rotationZ = rotation),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = ListUIConfig.STROKE_WIDTH.dp,
            )
            Spacer(modifier = Modifier.height(ListUIConfig.LARGE_SPACER_HEIGHT.dp))
            Text(
                text = "Loading beautiful items...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SuccessState(
    itemsModel: ItemsModel,
    onItemClick: (id: String) -> Unit,
    showItems: Boolean,
    animationTrigger: Int,
    selectedAnimation: AnimationType,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(ListUIConfig.CONTENT_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(ListUIConfig.ITEM_SPACING.dp),
    ) {
        itemsModel.items.chunked(ListUIConfig.ITEMS_CHUNK_SIZE).forEachIndexed { chunkIndex, items
            ->
            item {
                AnimatedVisibility(
                    visible = showItems,
                    enter =
                        fadeIn(
                            animationSpec =
                                tween(
                                    ListAnimationConfig.FADE_DURATION,
                                    delayMillis =
                                        chunkIndex * ListAnimationConfig.SECTION_HEADER_DELAY,
                                )
                        ) +
                            slideInVertically(
                                animationSpec =
                                    tween(
                                        ListAnimationConfig.FADE_DURATION,
                                        delayMillis =
                                            chunkIndex * ListAnimationConfig.SECTION_HEADER_DELAY,
                                    ),
                                initialOffsetY = { it / 2 },
                            ),
                ) {
                    StickyHeader(index = chunkIndex)
                }
                Spacer(modifier = Modifier.height(ListUIConfig.SPACER_HEIGHT.dp))
            }

            itemsIndexed(items) { itemIndex, item ->
                val globalItemIndex = chunkIndex * ListUIConfig.ITEMS_CHUNK_SIZE + itemIndex

                AnimatedVisibility(
                    visible = showItems,
                    enter =
                        fadeIn(
                            animationSpec =
                                tween(
                                    ListAnimationConfig.ITEM_ANIMATION_DURATION,
                                    delayMillis =
                                        globalItemIndex * ListAnimationConfig.STAGGER_DELAY,
                                )
                        ) +
                            slideInVertically(
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                                initialOffsetY = { it / 2 },
                            ),
                ) {
                    ItemCard(
                        item = item,
                        onItemClick = onItemClick,
                        itemIndex = globalItemIndex,
                        animationTrigger = animationTrigger,
                        selectedAnimation = selectedAnimation,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FailureState(throwable: Throwable, modifier: Modifier = Modifier) {
    val (scale, rotation) = useErrorAnimation()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier =
                    Modifier.size(ListUIConfig.ERROR_ICON_SIZE.dp)
                        .scale(scale)
                        .graphicsLayer(rotationZ = rotation),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "Error",
                        modifier = Modifier.size(ListUIConfig.ERROR_ICON_INNER_SIZE.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(ListUIConfig.LARGE_SPACER_HEIGHT.dp))

            Text(
                text = "Oops! Something went wrong",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(ListUIConfig.SPACER_HEIGHT.dp))

            Text(
                text = throwable.message ?: "An unexpected error occurred",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(ListUIConfig.LARGE_SPACER_HEIGHT.dp))

            Text(
                text = "Tap the refresh button to try again",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
