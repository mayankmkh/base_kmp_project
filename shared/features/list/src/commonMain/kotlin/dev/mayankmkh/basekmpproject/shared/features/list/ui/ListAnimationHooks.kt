package dev.mayankmkh.basekmpproject.shared.features.list.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Composable
fun useStaggeredVisibility(
    trigger: Boolean,
    initialDelay: Int = ListAnimationConfig.INITIAL_DELAY,
    showDelay: Int = ListAnimationConfig.ITEMS_SHOW_DELAY,
): Boolean {
    var showItems by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        if (trigger) {
            delay(initialDelay.toLong())
            showItems = true
        } else {
            showItems = false
        }
    }

    return showItems
}

@Composable
fun useAnimationTrigger(
    dependency: Any?,
    delayMillis: Int = ListAnimationConfig.ITEMS_SHOW_DELAY,
): Int {
    var trigger by remember { mutableStateOf(0) }

    LaunchedEffect(dependency) {
        if (dependency != null) {
            delay(delayMillis.toLong())
            trigger++
        }
    }

    return trigger
}

@Composable
fun useItemAnimation(
    trigger: Int,
    itemIndex: Int,
    animationType: AnimationType,
    isActive: Boolean = true,
): AnimationValue {
    val strategy = remember(animationType) { AnimationStrategyFactory.create(animationType) }
    return strategy.animate(trigger, itemIndex, isActive)
}

@Composable
fun useBreathingEffect(
    delayMillis: Int = 0,
    minScale: Float = ListAnimationConfig.BREATH_SCALE_MIN,
    maxScale: Float = ListAnimationConfig.BREATH_SCALE_MAX,
    duration: Int = ListAnimationConfig.BREATH_DURATION,
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    return infiniteTransition
        .animateFloat(
            initialValue = minScale,
            targetValue = maxScale,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(duration, delayMillis = delayMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "breath",
        )
        .value
}

@Composable
fun usePressedScale(
    isPressed: Boolean,
    targetScale: Float = ListAnimationConfig.PRESSED_SCALE,
    dampingRatio: Float = ListAnimationConfig.SCALE_DAMPING_RATIO,
    stiffness: Float = ListAnimationConfig.SCALE_STIFFNESS,
): Float {
    return animateFloatAsState(
            targetValue = if (isPressed) targetScale else 1f,
            animationSpec = spring(dampingRatio = dampingRatio, stiffness = stiffness),
            label = "pressedScale",
        )
        .value
}

@Composable
fun usePulseAnimation(
    minScale: Float = ListAnimationConfig.INITIAL_PULSE_MIN,
    maxScale: Float = ListAnimationConfig.INITIAL_PULSE_MAX,
    duration: Int = ListAnimationConfig.INITIAL_PULSE_DURATION,
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    return infiniteTransition
        .animateFloat(
            initialValue = minScale,
            targetValue = maxScale,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(duration, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "pulse",
        )
        .value
}

@Composable
fun useRotationAnimation(duration: Int = ListAnimationConfig.LOADING_ROTATION_DURATION): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    return infiniteTransition
        .animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(duration, easing = LinearEasing)),
            label = "rotation",
        )
        .value
}

@Composable
fun useFloatingAnimation(
    offset: Float = ListAnimationConfig.HEADER_FLOAT_OFFSET,
    duration: Int = ListAnimationConfig.HEADER_FLOAT_DURATION,
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "floating")
    return infiniteTransition
        .animateFloat(
            initialValue = 0f,
            targetValue = offset,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(duration, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "float",
        )
        .value
}

@Composable
fun useErrorAnimation(): Pair<Float, Float> {
    val infiniteTransition = rememberInfiniteTransition(label = "error")

    val scale by
        infiniteTransition.animateFloat(
            initialValue = ListAnimationConfig.ERROR_SCALE_MIN,
            targetValue = ListAnimationConfig.ERROR_SCALE_MAX,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(ListAnimationConfig.ERROR_PULSE_DURATION, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "errorScale",
        )

    val rotation by
        infiniteTransition.animateFloat(
            initialValue = ListAnimationConfig.ERROR_ROTATION_MIN,
            targetValue = ListAnimationConfig.ERROR_ROTATION_MAX,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(ListAnimationConfig.ERROR_SHAKE_DURATION, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "errorRotation",
        )

    return Pair(scale, rotation)
}
