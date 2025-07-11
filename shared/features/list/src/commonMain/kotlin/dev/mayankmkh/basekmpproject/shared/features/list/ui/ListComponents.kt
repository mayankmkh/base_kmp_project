package dev.mayankmkh.basekmpproject.shared.features.list.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mayankmkh.basekmpproject.shared.features.list.domain.Item
import kotlinx.coroutines.delay

@Composable
fun StickyHeader(index: Int, modifier: Modifier = Modifier) {
    val offsetY = useFloatingAnimation()

    Surface(
        modifier = modifier.fillMaxWidth().graphicsLayer(translationY = offsetY),
        shape = RoundedCornerShape(ListUIConfig.CARD_CORNER_RADIUS.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = ListUIConfig.HEADER_ELEVATION.dp,
    ) {
        Text(
            text = "Section ${index + 1}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(ListUIConfig.SECTION_HEADER_PADDING.dp),
        )
    }
}

@Composable
internal fun ItemCard(
    item: Item,
    onItemClick: (id: String) -> Unit,
    itemIndex: Int,
    animationTrigger: Int,
    selectedAnimation: AnimationType,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }

    // Animation trigger for this specific item
    var localAnimationTrigger by remember { mutableStateOf(0) }

    // Trigger animation when global trigger changes
    LaunchedEffect(animationTrigger) {
        if (animationTrigger > 0) {
            delay(itemIndex * ListAnimationConfig.ITEM_DELAY_MULTIPLIER.toLong())
            localAnimationTrigger++
        }
    }

    // Get animation values
    val animationValue =
        useItemAnimation(
            trigger = localAnimationTrigger,
            itemIndex = itemIndex,
            animationType = selectedAnimation,
        )

    val scale = usePressedScale(isPressed)
    val breathScale =
        useBreathingEffect(delayMillis = itemIndex * ListAnimationConfig.ITEM_STAGGER_DELAY)

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .scale(scale * breathScale * animationValue.scale)
                .graphicsLayer(
                    rotationY = animationValue.rotationY,
                    rotationZ = animationValue.rotationZ,
                    translationX = animationValue.translationX,
                    translationY = animationValue.translationY,
                    alpha = animationValue.alpha,
                )
                .clickable {
                    isPressed = true
                    onItemClick(item.id)
                },
        shape = RoundedCornerShape(ListUIConfig.CARD_CORNER_RADIUS.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = ListUIConfig.CARD_ELEVATION.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(ListUIConfig.CARD_PADDING.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemAvatar(itemId = item.id, isPressed = isPressed)

            Spacer(modifier = Modifier.width(ListUIConfig.SPACER_WIDTH.dp))

            ItemContent(title = item.title, description = item.text)
        }
    }
}

@Composable
private fun ItemAvatar(itemId: String, isPressed: Boolean, modifier: Modifier = Modifier) {
    val avatarScale =
        usePressedScale(
            isPressed = isPressed,
            targetScale = ListAnimationConfig.AVATAR_PRESSED_SCALE,
            dampingRatio = ListAnimationConfig.AVATAR_DAMPING_RATIO,
            stiffness = ListAnimationConfig.AVATAR_STIFFNESS,
        )

    Surface(
        modifier = modifier.size(ListUIConfig.AVATAR_SIZE.dp).scale(avatarScale),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = itemId,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ItemContent(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = ListUIConfig.TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(ListUIConfig.SMALL_SPACER_HEIGHT.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = ListUIConfig.DESCRIPTION_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AnimationSelector(
    selectedAnimation: AnimationType,
    onAnimationSelected: (AnimationType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(ListUIConfig.ANIMATION_SELECTOR_PADDING.dp),
        shape = RoundedCornerShape(ListUIConfig.ANIMATION_SELECTOR_CORNER_RADIUS.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = ListUIConfig.ANIMATION_SELECTOR_ELEVATION.dp,
    ) {
        Column(modifier = Modifier.padding(ListUIConfig.SPACER_HEIGHT.dp)) {
            Text(
                text = "Choose Animation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(ListUIConfig.ANIMATION_SELECTOR_ITEM_PADDING.dp),
            )

            AnimationType.entries.forEach { animationType ->
                AnimationSelectorItem(
                    animationType = animationType,
                    isSelected = selectedAnimation == animationType,
                    onSelected = { onAnimationSelected(animationType) },
                )
            }
        }
    }
}

@Composable
private fun AnimationSelectorItem(
    animationType: AnimationType,
    isSelected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onSelected() }
                .padding(vertical = ListUIConfig.ANIMATION_SELECTOR_VERTICAL_PADDING.dp),
        shape = RoundedCornerShape(ListUIConfig.ANIMATION_SELECTOR_ITEM_CORNER_RADIUS.dp),
        color =
            if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = animationType.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(ListUIConfig.ANIMATION_SELECTOR_ITEM_PADDING.dp),
        )
    }
}

@Composable
fun AnimationSelectorButton(
    selectedAnimation: AnimationType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier.padding(end = ListUIConfig.ANIMATION_BUTTON_END_PADDING.dp).clickable {
                onClick()
            },
        shape = RoundedCornerShape(ListUIConfig.SURFACE_CORNER_RADIUS.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = selectedAnimation.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier =
                Modifier.padding(
                    horizontal = ListUIConfig.ANIMATION_BUTTON_HORIZONTAL_PADDING.dp,
                    vertical = ListUIConfig.ANIMATION_BUTTON_VERTICAL_PADDING.dp,
                ),
        )
    }
}
