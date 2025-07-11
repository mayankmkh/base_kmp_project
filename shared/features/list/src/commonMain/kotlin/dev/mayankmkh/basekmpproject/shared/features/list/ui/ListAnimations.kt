package dev.mayankmkh.basekmpproject.shared.features.list.ui

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

enum class AnimationType(val displayName: String) {
    FLIP("3D Flip"),
    BOUNCE("Bounce"),
    SPIN("Spin"),
    ZOOM("Zoom"),
    SLIDE("Slide"),
    FADE("Fade"),
    WAVE("Wave"),
    PULSE("Pulse"),
}

data class AnimationValue(
    val scale: Float = 1f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val alpha: Float = 1f,
) {
    companion object {
        val Default = AnimationValue()
    }
}

interface AnimationStrategy {
    @Composable fun animate(trigger: Int, itemIndex: Int, isActive: Boolean = true): AnimationValue
}

class FlipAnimationStrategy : AnimationStrategy {
    @Composable
    override fun animate(trigger: Int, itemIndex: Int, isActive: Boolean): AnimationValue {
        val rotation by
            animateFloatAsState(
                targetValue = if (trigger > 0 && isActive) 360f else 0f,
                animationSpec = tween(800, delayMillis = itemIndex * 100),
                label = "flip",
            )
        return AnimationValue(rotationY = rotation)
    }
}

class BounceAnimationStrategy : AnimationStrategy {
    @Composable
    override fun animate(trigger: Int, itemIndex: Int, isActive: Boolean): AnimationValue {
        val bounce by
            animateFloatAsState(
                targetValue = if (trigger > 0 && isActive) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.3f, stiffness = 200f),
                label = "bounce",
            )
        return AnimationValue(scale = 1f + bounce * 0.3f)
    }
}

class SpinAnimationStrategy : AnimationStrategy {
    @Composable
    override fun animate(trigger: Int, itemIndex: Int, isActive: Boolean): AnimationValue {
        val spin by
            animateFloatAsState(
                targetValue = if (trigger > 0 && isActive) 720f else 0f,
                animationSpec = tween(1200, delayMillis = itemIndex * 100, easing = EaseInOut),
                label = "spin",
            )
        return AnimationValue(rotationZ = spin)
    }
}

class ZoomAnimationStrategy : AnimationStrategy {
    @Composable
    override fun animate(trigger: Int, itemIndex: Int, isActive: Boolean): AnimationValue {
        val zoom by
            animateFloatAsState(
                targetValue = if (trigger > 0 && isActive) 1f else 0f,
                animationSpec = tween(600, delayMillis = itemIndex * 100, easing = EaseOut),
                label = "zoom",
            )
        return AnimationValue(scale = 0.3f + zoom * 0.7f)
    }
}

class SlideAnimationStrategy : AnimationStrategy {
    @Composable
    override fun animate(trigger: Int, itemIndex: Int, isActive: Boolean): AnimationValue {
        val slide by
            animateFloatAsState(
                targetValue = if (trigger > 0 && isActive) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                label = "slide",
            )
        return AnimationValue(translationX = (1f - slide) * 200f)
    }
}

class FadeAnimationStrategy : AnimationStrategy {
    @Composable
    override fun animate(trigger: Int, itemIndex: Int, isActive: Boolean): AnimationValue {
        val fade by
            animateFloatAsState(
                targetValue = if (trigger > 0 && isActive) 1f else 0f,
                animationSpec = tween(500, delayMillis = itemIndex * 100, easing = EaseInOut),
                label = "fade",
            )
        return AnimationValue(alpha = fade)
    }
}

class WaveAnimationStrategy : AnimationStrategy {
    @Composable
    override fun animate(trigger: Int, itemIndex: Int, isActive: Boolean): AnimationValue {
        val wave by
            animateFloatAsState(
                targetValue = if (trigger > 0 && isActive) 1f else 0f,
                animationSpec = tween(800, delayMillis = itemIndex * 100, easing = EaseInOut),
                label = "wave",
            )
        return AnimationValue(translationY = (1f - wave) * 50f, rotationZ = (1f - wave) * 15f)
    }
}

class PulseAnimationStrategy : AnimationStrategy {
    @Composable
    override fun animate(trigger: Int, itemIndex: Int, isActive: Boolean): AnimationValue {
        val pulse by
            animateFloatAsState(
                targetValue = if (trigger > 0 && isActive) 1f else 0f,
                animationSpec = tween(400, delayMillis = itemIndex * 100, easing = EaseInOut),
                label = "pulse",
            )
        return AnimationValue(scale = 0.8f + pulse * 0.4f)
    }
}

object AnimationStrategyFactory {
    fun create(type: AnimationType): AnimationStrategy {
        return when (type) {
            AnimationType.FLIP -> FlipAnimationStrategy()
            AnimationType.BOUNCE -> BounceAnimationStrategy()
            AnimationType.SPIN -> SpinAnimationStrategy()
            AnimationType.ZOOM -> ZoomAnimationStrategy()
            AnimationType.SLIDE -> SlideAnimationStrategy()
            AnimationType.FADE -> FadeAnimationStrategy()
            AnimationType.WAVE -> WaveAnimationStrategy()
            AnimationType.PULSE -> PulseAnimationStrategy()
        }
    }
}
