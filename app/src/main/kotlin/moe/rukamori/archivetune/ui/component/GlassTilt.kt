/*
 * Spry (2026) | phone tilt for the glass highlights.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.component

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Tilt relative to how the phone is being held: a fast-smoothed gravity reading minus a slow
 * baseline, so the light swings when you move the phone and settles back when you hold still.
 * Listens only while the app is resumed. Phones without a gravity sensor just get Offset.Zero.
 */
@Composable
fun rememberGlassTilt(): State<Offset> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(lifecycle) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val listener = TiltListener(tilt)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                Lifecycle.Event.ON_PAUSE -> sm?.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sm?.unregisterListener(listener)
        }
    }
    return tilt
}

private class TiltListener(private val out: MutableState<Offset>) : SensorEventListener {
    private var fx = Float.NaN
    private var fy = 0f
    private var bx = 0f
    private var by = 0f

    override fun onSensorChanged(e: SensorEvent) {
        val gx = e.values[0]
        val gy = e.values[1]
        if (fx.isNaN()) { fx = gx; fy = gy; bx = gx; by = gy }
        fx += (gx - fx) * 0.25f
        fy += (gy - fy) * 0.25f
        bx += (gx - bx) * 0.02f
        by += (gy - by) * 0.02f
        // ~3 m/s² of movement (a modest tilt) swings the light fully
        val next = Offset(((fx - bx) / -3f).coerceIn(-1f, 1f), ((fy - by) / 3f).coerceIn(-1f, 1f))
        if ((next - out.value).getDistance() > 0.01f) out.value = next
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
