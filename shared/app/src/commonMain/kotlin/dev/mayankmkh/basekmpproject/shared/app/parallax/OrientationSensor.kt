package dev.mayankmkh.basekmpproject.shared.app.parallax

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow

data class Orientation(
    val pitch: Double = 0.0,
    val roll: Double = 0.0
)

interface OrientationSensor {
    val data: Flow<Orientation>
    fun dispose() {}
}

@Composable
expect fun rememberOrientationSensor(): OrientationSensor

/**
 * A controller that provides the current [orientation] and a [modifier]
 * that must be applied to the container handling input (if applicable, e.g. Desktop Mouse).
 */
interface TiltController {
    val orientation: State<Orientation>
    val modifier: Modifier
}

@Composable
expect fun rememberTiltController(): TiltController
