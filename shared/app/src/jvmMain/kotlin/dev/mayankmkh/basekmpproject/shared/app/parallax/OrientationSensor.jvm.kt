package dev.mayankmkh.basekmpproject.shared.app.parallax

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI

class JvmOrientationSensor : OrientationSensor {
    override val data: Flow<Orientation> = flow {
        emit(Orientation(0.0, 0.0)) // Desktop uses UI pointer events instead of global polling
    }
}

@Composable
actual fun rememberOrientationSensor(): OrientationSensor {
    return remember { JvmOrientationSensor() }
}

@Composable
actual fun rememberTiltController(): TiltController {
    val orientationState = remember { mutableStateOf(Orientation(0.0, 0.0)) }
    
    // We explicitly use 'remember' to ensure the Modifier chain is created only ONCE.
    // The lambda inside pointerInput captures 'orientationState' which is stable.
    val modifier = remember {
        val containerSize = mutableStateOf(IntSize.Zero)
        
        Modifier
            .onGloballyPositioned { containerSize.value = it.size }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.last().position
                        
                        val size = containerSize.value
                        if (size.width > 0 && size.height > 0) {
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            
                            val normX = (position.x - centerX) / centerX
                            val normY = (position.y - centerY) / centerY
                            
                            val maxTilt = PI / 6 
                            
                            orientationState.value = Orientation(
                                pitch = (normY * maxTilt).coerceIn(-maxTilt, maxTilt),
                                roll = (normX * maxTilt).coerceIn(-maxTilt, maxTilt)
                            )
                        }
                    }
                }
            }
    }

    return remember {
        object : TiltController {
            override val orientation: State<Orientation> = orientationState
            override val modifier: Modifier = modifier
        }
    }
}
