package dev.mayankmkh.basekmpproject.shared.app.parallax

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue

class IOSOrientationSensor : OrientationSensor {
    private val motionManager = CMMotionManager()

    override val data: Flow<Orientation> = callbackFlow {
        if (motionManager.deviceMotionAvailable) {
            motionManager.deviceMotionUpdateInterval = 1.0 / 60.0
            motionManager.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { motion, error ->
                if (motion != null) {
                    val attitude = motion.attitude
                    trySend(Orientation(pitch = attitude.pitch, roll = attitude.roll))
                }
            }
        }

        awaitClose {
            motionManager.stopDeviceMotionUpdates()
        }
    }
}

@Composable
actual fun rememberOrientationSensor(): OrientationSensor {
    return remember { IOSOrientationSensor() }
}

@Composable
actual fun rememberTiltController(): TiltController {
    val sensor = rememberOrientationSensor()
    val orientationState = sensor.data.collectAsState(Orientation())
    
    return remember(orientationState) {
        object : TiltController {
            override val orientation: State<Orientation> = orientationState
            override val modifier: Modifier = Modifier
        }
    }
}
