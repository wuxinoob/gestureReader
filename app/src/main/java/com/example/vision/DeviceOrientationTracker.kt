package com.example.vision

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.OrientationEventListener
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DeviceScreenOrientation(val degrees: Int, val surfaceRotation: Int, val label: String) {
    PORTRAIT(0, Surface.ROTATION_0, "竖屏 (Portrait)"),
    LANDSCAPE_LEFT(90, Surface.ROTATION_90, "横屏向左 (Landscape Left)"),
    REVERSE_PORTRAIT(180, Surface.ROTATION_180, "倒置竖屏 (Reverse Portrait)"),
    LANDSCAPE_RIGHT(270, Surface.ROTATION_270, "横屏向右 (Landscape Right)")
}

class DeviceOrientationTracker(private val context: Context) {

    private val _orientationFlow = MutableStateFlow(DeviceScreenOrientation.PORTRAIT)
    val orientationFlow: StateFlow<DeviceScreenOrientation> = _orientationFlow.asStateFlow()

    private var orientationListener: OrientationEventListener? = null
    private var sensorManager: SensorManager? = null
    private var gravitySensor: Sensor? = null

    // 设备当前旋转状态
    var currentSurfaceRotation: Int = Surface.ROTATION_0
        private set

    // 旋转回调监听器
    var onRotationChangedListener: ((newRotation: Int) -> Unit)? = null

    // 传感器倾角 (度)
    var devicePitchAngle = 0f
        private set
    var deviceRollAngle = 0f
        private set

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // 依据重力向量计算设备自身的倾斜程度
            devicePitchAngle = kotlin.math.atan2(y.toDouble(), kotlin.math.sqrt((x * x + z * z).toDouble())).toFloat() * 57.29578f
            deviceRollAngle = kotlin.math.atan2(x.toDouble(), kotlin.math.sqrt((y * y + z * z).toDouble())).toFloat() * 57.29578f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        // 1. 系统方向监听器 (精确感应重力旋转并映射到 Surface.ROTATION_*)
        orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val newOrientation = when {
                    orientation >= 315 || orientation < 45 -> DeviceScreenOrientation.PORTRAIT
                    orientation in 45..134 -> DeviceScreenOrientation.LANDSCAPE_RIGHT
                    orientation in 135..224 -> DeviceScreenOrientation.REVERSE_PORTRAIT
                    orientation in 225..314 -> DeviceScreenOrientation.LANDSCAPE_LEFT
                    else -> DeviceScreenOrientation.PORTRAIT
                }

                if (_orientationFlow.value != newOrientation) {
                    _orientationFlow.value = newOrientation
                    val newRotation = newOrientation.surfaceRotation
                    if (currentSurfaceRotation != newRotation) {
                        currentSurfaceRotation = newRotation
                        onRotationChangedListener?.invoke(newRotation)
                    }
                }
            }
        }

        if (orientationListener?.canDetectOrientation() == true) {
            orientationListener?.enable()
        }

        // 2. 重力/加速度传感器，检测设备倾角
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        gravitySensor?.let { sensor ->
            sensorManager?.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun pause() {
        try {
            orientationListener?.disable()
            sensorManager?.unregisterListener(sensorEventListener)
        } catch (_: Exception) {}
    }

    fun resume() {
        try {
            orientationListener?.enable()
            gravitySensor?.let { sensor ->
                sensorManager?.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (_: Exception) {}
    }

    /**
     * 将人脸欧拉角依据屏幕方向进行校准：
     * 由于 CameraX 的 ImageAnalysis.setTargetRotation 保证了传递给 ML Kit 的图像始终
     * 保持与当前屏幕视口方向一致 (Upright)，因此 ML Kit 测出的人脸欧拉角 Pitch(仰俯)、
     * Yaw(偏转) 与 Roll(侧倾) 已经天然对齐当前屏幕坐标系！
     * 此处保持坐标系一致性，防止历史版本错误颠倒 Pitch 与 Yaw 导致横屏无法识别。
     */
    fun compensateHeadPose(
        rawPitch: Float,
        rawYaw: Float,
        rawRoll: Float
    ): Triple<Float, Float, Float> {
        return Triple(rawPitch, rawYaw, rawRoll)
    }

    /**
     * 将眼动视线向量依据屏幕方向校准
     */
    fun compensateGaze(rawOffsetX: Float, rawOffsetY: Float): Pair<Float, Float> {
        return rawOffsetX to rawOffsetY
    }

    /**
     * 屏幕长宽比自适应眼动阈值：
     * 横屏模式下视口水平更宽、垂直更矮，因此适度放大横向防误触阈值、收敛纵向下看阈值。
     */
    fun getAdaptiveGazeThresholds(
        baseHThreshold: Float,
        baseVThreshold: Float
    ): Pair<Float, Float> {
        return when (_orientationFlow.value) {
            DeviceScreenOrientation.PORTRAIT, DeviceScreenOrientation.REVERSE_PORTRAIT -> {
                baseHThreshold to baseVThreshold
            }
            DeviceScreenOrientation.LANDSCAPE_LEFT, DeviceScreenOrientation.LANDSCAPE_RIGHT -> {
                (baseHThreshold * 1.25f).coerceIn(0.15f, 0.50f) to
                (baseVThreshold * 0.80f).coerceIn(0.12f, 0.45f)
            }
        }
    }

    fun stop() {
        try {
            orientationListener?.disable()
            sensorManager?.unregisterListener(sensorEventListener)
        } catch (_: Exception) {}
    }
}
