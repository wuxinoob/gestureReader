package com.example.vision

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.model.ActionType
import com.example.model.GestureCategory
import com.example.model.PerformanceMetrics
import com.example.model.RecognitionResult
import com.example.model.TriggerGesture
import com.example.model.UserConfig
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VisionProcessor(
    private val context: Context,
    private val onActionTriggered: (TriggerGesture, ActionType) -> Unit
) {
    private val faceTracker = FaceTracker()
    private val handAnalyzer = HandGestureAnalyzer()
    private val gazeTracker = GazeTracker()
    private val orientationTracker = DeviceOrientationTracker(context)

    init {
        orientationTracker.start()
    }

    // 状态流
    private val _recognitionResult = MutableStateFlow(RecognitionResult())
    val recognitionResult: StateFlow<RecognitionResult> = _recognitionResult.asStateFlow()

    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()

    // 映射表与配置
    private var activeBindings: Map<TriggerGesture, Pair<ActionType, Boolean>> = emptyMap()
    private var config: UserConfig = UserConfig()

    // 性能指标滑动统计
    private var frameCounter = 0
    private var droppedFrames = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var currentFps = 0f
    private var lastInferenceDuration = 0L

    // 触发蓄力与冷却计时
    private var activeCandidateGesture: TriggerGesture? = null
    private var candidateStartTime = 0L
    private var lastTriggeredTimestamp = 0L

    private val isProcessing = java.util.concurrent.atomic.AtomicBoolean(false)

    // 触觉与声音反馈
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var toneGenerator: ToneGenerator? = null
    private var resumeTimestamp: Long = System.currentTimeMillis()
    private var lastFaceDetectedTimestamp: Long = System.currentTimeMillis()
    private var settleUntilTimestamp: Long = 0L

    fun onDeviceRotationChanged(newRotation: Int) {
        activeCandidateGesture = null
        candidateStartTime = 0L
        settleUntilTimestamp = System.currentTimeMillis() + 800L
        _recognitionResult.value = _recognitionResult.value.copy(
            statusMessage = "屏幕已旋转，识别已自适应重置"
        )
    }

    fun pause() {
        this.config = this.config.copy(isPaused = true)
        activeCandidateGesture = null
        candidateStartTime = 0L
    }

    fun resume() {
        this.config = this.config.copy(isPaused = false)
        resumeTimestamp = System.currentTimeMillis()
        settleUntilTimestamp = System.currentTimeMillis() + 1000L
        lastFaceDetectedTimestamp = System.currentTimeMillis()
    }

    fun updateConfig(newConfig: UserConfig) {
        if (this.config.isPaused && !newConfig.isPaused) {
            resumeTimestamp = System.currentTimeMillis()
            settleUntilTimestamp = System.currentTimeMillis() + 1000L
            lastFaceDetectedTimestamp = System.currentTimeMillis()
        }
        this.config = newConfig
    }

    fun updateBindings(bindings: Map<TriggerGesture, Pair<ActionType, Boolean>>) {
        this.activeBindings = bindings
    }

    @OptIn(ExperimentalGetImage::class)
    fun processImage(imageProxy: ImageProxy, scope: CoroutineScope) {
        val frameStartTime = System.currentTimeMillis()
        frameCounter++
        val now = System.currentTimeMillis()

        // 功耗优化 1: 翻页后冷却期节电机制 (冷却期内用户无法触发动作，隔6帧轻量采样即可)
        val inCooldown = (now - lastTriggeredTimestamp) < (config.cooldownMs - 150L)
        if (inCooldown && (frameCounter % 6 != 0)) {
            droppedFrames++
            try { imageProxy.close() } catch (_: Exception) {}
            return
        }

        // 功耗优化 2: 智能离镜休眠哨兵模式 (无人脸3秒后降频至 2.5 FPS，立省 70% 功耗与发热)
        val isIdleNoFace = (now - lastFaceDetectedTimestamp) > 3000L
        if (isIdleNoFace && config.autoIdleSleep && (frameCounter % 8 != 0)) {
            droppedFrames++
            try { imageProxy.close() } catch (_: Exception) {}
            return
        }

        // 常规帧跳过机制 (Frame Skipping for CPU / Latency optimization)
        val skipRate = config.frameSkipRate.coerceAtLeast(1)
        if (frameCounter % skipRate != 0) {
            droppedFrames++
            try { imageProxy.close() } catch (_: Exception) {}
            return
        }

        if (config.isPaused || !isProcessing.compareAndSet(false, true)) {
            droppedFrames++
            try { imageProxy.close() } catch (_: Exception) {}
            return
        }

        // 计算 FPS
        if (now - lastFpsTimestamp >= 1000L) {
            currentFps = (frameCounter * 1000f) / (now - lastFpsTimestamp)
            frameCounter = 0
            lastFpsTimestamp = now
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isProcessing.set(false)
            try { imageProxy.close() } catch (_: Exception) {}
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = try {
            InputImage.fromMediaImage(mediaImage, rotationDegrees)
        } catch (_: Exception) {
            isProcessing.set(false)
            try { imageProxy.close() } catch (_: Exception) {}
            return
        }

        if (!scope.isActive) {
            isProcessing.set(false)
            try { imageProxy.close() } catch (_: Exception) {}
            return
        }

        scope.launch(Dispatchers.Default) {
            try {
                val inferenceStart = System.currentTimeMillis()

                // 1. 本地人脸与头部姿态识别 (ML Kit Face)
                val faceData = faceTracker.detectFace(inputImage)
                if (faceData.hasFace) {
                    lastFaceDetectedTimestamp = now
                }

                // 2. 本地手势分析 (智能按需执行：当开启了手部手势或处于调试监控面板时执行)
                val hasHandGestureBindings = activeBindings.any { (g, pair) ->
                    pair.second && g.category == GestureCategory.HAND
                } || config.showPerformanceHud
                val handData = if (hasHandGestureBindings) {
                    handAnalyzer.analyze(imageProxy, rotationDegrees, faceData.rawFace?.boundingBox)
                } else {
                    HandDetectionResult(hasHand = false)
                }

                // 3. 本地眼动与视线注视追踪 (仅在开启视线追踪且检测到人脸时执行)
                val gazeData = if (config.gazeTrackingEnabled && faceData.hasFace && faceData.rawFace != null) {
                    gazeTracker.estimateGaze(faceData.rawFace, imageProxy, config, orientationTracker, rotationDegrees)
                } else {
                    GazeResult(isTracked = false)
                }

                val inferenceEnd = System.currentTimeMillis()
                lastInferenceDuration = inferenceEnd - inferenceStart

                // 4. 评估手势与眼动触发
                evaluateGesture(faceData, handData, gazeData, now)

                // 4. 更新性能监控数据
                val totalFrameTime = System.currentTimeMillis() - frameStartTime
                val cpuEstimate = (lastInferenceDuration.toFloat() / 33.3f).coerceIn(0f, 1f)

                _performanceMetrics.value = PerformanceMetrics(
                    fps = currentFps,
                    inferenceTimeMs = lastInferenceDuration,
                    totalFrameTimeMs = totalFrameTime,
                    droppedFramesCount = droppedFrames,
                    faceConfidence = faceData.confidence,
                    isFaceTracked = faceData.hasFace,
                    isHandTracked = handData.hasHand,
                    cpuLoadEstimate = cpuEstimate,
                    framesProcessedCount = frameCounter.toLong()
                )
            } catch (_: kotlinx.coroutines.CancellationException) {
                // 协程正常取消 (如用户切换/挂起/销毁服务)，属于正常控制流，忽略无需上报
            } catch (e: Exception) {
                android.util.Log.e("VisionProcessor", "Frame processing exception: ${e.message}")
            } finally {
                isProcessing.set(false)
                try {
                    imageProxy.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun evaluateGesture(
        faceData: FaceRawData,
        handData: HandDetectionResult,
        gazeData: GazeResult,
        now: Long
    ) {
        // 如果在冷却期或刚启动/旋转设备后的安全过渡期内，不触发动作，给用户准备稳定时间
        val isSettling = (now - resumeTimestamp) < 2000L || now < settleUntilTimestamp
        val inCooldown = (now - lastTriggeredTimestamp) < config.cooldownMs || isSettling

        var detected: TriggerGesture? = null

        var compPitch = faceData.pitch
        var compYaw = faceData.yaw
        var compRoll = faceData.roll

        if (faceData.hasFace) {
            val compensated = orientationTracker.compensateHeadPose(
                faceData.pitch,
                faceData.yaw,
                faceData.roll
            )
            // 姿态基准零点相对标定：减去用户一键标定的舒适阅读角度
            compPitch = compensated.first - config.baselinePitch
            compYaw = compensated.second - config.baselineYaw
            compRoll = compensated.third - config.baselineRoll

            val effPitch = config.effectivePitchThreshold()
            val effYaw = config.effectiveYawThreshold()
            val effRoll = config.effectiveRollThreshold()

            // 头部动作优先检测 (已根据设备物理朝向与陀螺仪/重力补偿)
            when {
                compPitch > effPitch -> detected = TriggerGesture.HEAD_PITCH_UP
                compPitch < -effPitch -> detected = TriggerGesture.HEAD_PITCH_DOWN
                compYaw > effYaw -> detected = TriggerGesture.HEAD_YAW_RIGHT
                compYaw < -effYaw -> detected = TriggerGesture.HEAD_YAW_LEFT
                compRoll > effRoll -> detected = TriggerGesture.HEAD_ROLL_RIGHT
                compRoll < -effRoll -> detected = TriggerGesture.HEAD_ROLL_LEFT
                // 面部动作检测
                faceData.leftEyeOpenProb < config.eyeBlinkThreshold && faceData.rightEyeOpenProb < config.eyeBlinkThreshold ->
                    detected = TriggerGesture.BLINK_BOTH
                faceData.leftEyeOpenProb < config.eyeBlinkThreshold && faceData.rightEyeOpenProb > 0.6f ->
                    detected = TriggerGesture.BLINK_LEFT
                faceData.rightEyeOpenProb < config.eyeBlinkThreshold && faceData.leftEyeOpenProb > 0.6f ->
                    detected = TriggerGesture.BLINK_RIGHT
                faceData.mouthOpenRatio > config.mouthOpenThreshold ->
                    detected = TriggerGesture.MOUTH_OPEN
                faceData.smilingProb > config.smileThreshold ->
                    detected = TriggerGesture.SMILE
            }

            // 若无明显头部或面部动作，检测眼动视线动作
            if (detected == null && gazeData.isTracked && gazeData.detectedGesture != null) {
                detected = gazeData.detectedGesture
            }
        }

        // 如果头部和眼动没有明确动作，检测手势
        if (detected == null && handData.hasHand && handData.gesture != null) {
            detected = handData.gesture
        }

        // 检查该手势是否被绑定且启用
        val bindingInfo = detected?.let { activeBindings[it] }
        val isEnabled = bindingInfo?.second == true
        val targetAction = bindingInfo?.first

        var progress = 0f

        if (!inCooldown && detected != null && isEnabled && targetAction != null && targetAction != ActionType.NONE) {
            val isDynamicSwipe = (detected == TriggerGesture.HAND_SWIPE_LEFT ||
                                  detected == TriggerGesture.HAND_SWIPE_RIGHT ||
                                  detected == TriggerGesture.HAND_POINT_UP ||
                                  detected == TriggerGesture.HAND_POINT_DOWN)

            if (isDynamicSwipe) {
                // 动态挥手属于瞬间动作，轨迹特征一旦确认满足即刻触发，无需持续蓄力！
                fireTrigger(detected, targetAction, now)
                handAnalyzer.reset()
                activeCandidateGesture = null
                progress = 1.0f
            } else if (activeCandidateGesture == detected) {
                val elapsed = now - candidateStartTime
                val requiredHold = when {
                    detected.category == GestureCategory.GAZE -> config.effectiveGazeDwellMs()
                    detected.category == GestureCategory.HAND -> (config.effectiveHoldDurationMs() * 0.70f).toLong().coerceIn(120L, 800L)
                    else -> config.effectiveHoldDurationMs()
                }
                progress = (elapsed.toFloat() / requiredHold).coerceIn(0f, 1f)

                // 蓄力完成，正式触发！
                if (progress >= 1.0f) {
                    fireTrigger(detected, targetAction, now)
                    activeCandidateGesture = null
                    progress = 0f
                }
            } else {
                // 新手势候选
                activeCandidateGesture = detected
                candidateStartTime = now
                progress = 0.05f
            }
        } else {
            // 没有动作，逐渐重置
            activeCandidateGesture = null
            progress = 0f
        }

        _recognitionResult.value = _recognitionResult.value.copy(
            headPitch = compPitch,
            headYaw = compYaw,
            headRoll = compRoll,
            leftEyeOpenProb = faceData.leftEyeOpenProb,
            rightEyeOpenProb = faceData.rightEyeOpenProb,
            mouthOpenRatio = faceData.mouthOpenRatio,
            smilingProb = faceData.smilingProb,
            gazeOffsetX = gazeData.gazeOffsetX,
            gazeOffsetY = gazeData.gazeOffsetY,
            isGazeTracked = gazeData.isTracked,
            handGesture = handData.gesture,
            handConfidence = handData.confidence,
            candidateGesture = activeCandidateGesture,
            triggerHoldProgress = progress,
            isTrackingActive = !config.isPaused,
            statusMessage = when {
                config.isPaused -> "识别已暂停"
                isSettling -> "🟢 视角准备就绪..."
                inCooldown -> "冷却中..."
                activeCandidateGesture != null -> "蓄力中: ${activeCandidateGesture?.displayName}"
                faceData.hasFace -> "正在监测头部与手势 (${orientationTracker.orientationFlow.value.label})"
                else -> "等待前置人脸入镜..."
            }
        )
    }

    private fun fireTrigger(gesture: TriggerGesture, action: ActionType, timestamp: Long) {
        lastTriggeredTimestamp = timestamp

        // 触觉反馈
        if (config.hapticFeedback && vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }

        // 声音反馈
        if (config.soundFeedback) {
            try {
                if (toneGenerator == null) {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
                }
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
            } catch (_: Exception) {}
        }

        _recognitionResult.value = _recognitionResult.value.copy(
            lastFiredGesture = gesture,
            lastFiredAction = action,
            lastFiredTimestamp = timestamp
        )

        // 调试状态模式：若开启了仅调试免干扰，仅展示触觉与状态反馈，绝不执行底层系统动作模拟
        if (config.debugModeNoTrigger) {
            return
        }

        onActionTriggered(gesture, action)
    }

    fun close() {
        orientationTracker.stop()
        faceTracker.close()
        handAnalyzer.reset()
        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
    }
}
