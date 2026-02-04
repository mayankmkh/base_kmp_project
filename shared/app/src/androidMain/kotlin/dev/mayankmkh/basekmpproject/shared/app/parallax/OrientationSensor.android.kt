package dev.mayankmkh.basekmpproject.shared.app.parallax

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidOrientationSensor(private val context: Context) : OrientationSensor {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    override val data: Flow<Orientation> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                     val rotationMatrix = FloatArray(9)
                     SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                     val orientationAngles = FloatArray(3)
                     SensorManager.getOrientation(rotationMatrix, orientationAngles)
                     // orientationAngles: azimuth, pitch, roll (radians)
                     trySend(Orientation(pitch = orientationAngles[1].toDouble(), roll = orientationAngles[2].toDouble()))
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor != null) {
             sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}

@Composable
actual fun rememberOrientationSensor(): OrientationSensor {
    val context = LocalContext.current
    return remember(context) { AndroidOrientationSensor(context) }
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
