package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.GestureReaderApp
import com.example.data.GestureBindingEntity
import com.example.model.ActionType
import com.example.model.PerformanceMetrics
import com.example.model.RecognitionResult
import com.example.model.TriggerGesture
import com.example.model.UserConfig
import com.example.service.GestureAccessibilityService
import com.example.service.OverlayTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab(val title: String, val icon: String) {
    HOME("控制台", "dashboard"),
    BINDINGS("动作映射", "tune"),
    PERFORMANCE("实时性能", "speed"),
    READER("沉浸阅读", "menu_book"),
    SETTINGS("高级配置", "settings")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as GestureReaderApp).repository

    val userConfig: StateFlow<UserConfig> = repository.configState

    val bindings: StateFlow<List<GestureBindingEntity>> = repository.allBindings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val isOverlayRunning: StateFlow<Boolean> = OverlayTrackingService.isServiceRunning
    val isAccessibilityBound: StateFlow<Boolean> = GestureAccessibilityService.isServiceBound

    val recognitionResult: StateFlow<RecognitionResult> = OverlayTrackingService.currentRecognition
    val performanceMetrics: StateFlow<PerformanceMetrics> = OverlayTrackingService.currentMetrics

    private val _currentTab = MutableStateFlow(AppTab.HOME)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _systemAlertPermission = MutableStateFlow(hasOverlayPermission(application))
    val systemAlertPermission: StateFlow<Boolean> = _systemAlertPermission.asStateFlow()

    private val _accessibilityPermission = MutableStateFlow(
        GestureAccessibilityService.isAccessibilityEnabled(application)
    )
    val accessibilityPermission: StateFlow<Boolean> = _accessibilityPermission.asStateFlow()

    fun switchTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun refreshPermissions() {
        val app = getApplication<Application>()
        _systemAlertPermission.value = hasOverlayPermission(app)
        _accessibilityPermission.value = GestureAccessibilityService.isAccessibilityEnabled(app)
    }

    fun startOverlayService(context: Context) {
        try {
            val intent = Intent(context, OverlayTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Failed to start overlay service: ${e.message}")
        }
    }

    fun stopOverlayService(context: Context) {
        try {
            val intent = Intent(context, OverlayTrackingService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Failed to stop overlay service: ${e.message}")
        }
    }

    fun calibrateCurrentPostureAsZeroPoint(): Boolean {
        val current = recognitionResult.value
        val curConfig = userConfig.value
        val newPitch = (curConfig.baselinePitch + current.headPitch).coerceIn(-60f, 60f)
        val newYaw = (curConfig.baselineYaw + current.headYaw).coerceIn(-60f, 60f)
        val newRoll = (curConfig.baselineRoll + current.headRoll).coerceIn(-60f, 60f)

        // 若当前成功捕获到视线，同步校准视线中心相对零点！
        val newGazeX = if (current.isGazeTracked) {
            (curConfig.baselineGazeX + current.gazeOffsetX).coerceIn(-0.6f, 0.6f)
        } else curConfig.baselineGazeX

        val newGazeY = if (current.isGazeTracked) {
            (curConfig.baselineGazeY + current.gazeOffsetY).coerceIn(-0.6f, 0.6f)
        } else curConfig.baselineGazeY

        viewModelScope.launch {
            repository.updateConfig {
                it.copy(
                    baselinePitch = newPitch,
                    baselineYaw = newYaw,
                    baselineRoll = newRoll,
                    baselineGazeX = newGazeX,
                    baselineGazeY = newGazeY
                )
            }
        }
        return true
    }

    fun calibrateGazeZeroPoint(): Boolean {
        val current = recognitionResult.value
        if (!current.isGazeTracked) return false
        val curConfig = userConfig.value
        val newGazeX = (curConfig.baselineGazeX + current.gazeOffsetX).coerceIn(-0.6f, 0.6f)
        val newGazeY = (curConfig.baselineGazeY + current.gazeOffsetY).coerceIn(-0.6f, 0.6f)
        viewModelScope.launch {
            repository.updateConfig {
                it.copy(
                    baselineGazeX = newGazeX,
                    baselineGazeY = newGazeY
                )
            }
        }
        return true
    }

    fun resetPostureZeroPoint() {
        viewModelScope.launch {
            repository.updateConfig {
                it.copy(
                    baselinePitch = 0f,
                    baselineYaw = 0f,
                    baselineRoll = 0f,
                    baselineGazeX = 0f,
                    baselineGazeY = 0f
                )
            }
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            repository.updateConfig { it.copy(isDarkMode = !it.isDarkMode) }
        }
    }

    fun setDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            repository.updateConfig { it.copy(isDarkMode = isDark) }
        }
    }

    fun applyPreset(preset: com.example.model.ConfigPreset) {
        viewModelScope.launch {
            repository.updateConfig { it.withPreset(preset) }
        }
    }

    fun toggleTrackingPause() {
        viewModelScope.launch {
            repository.updateConfig { it.copy(isPaused = !it.isPaused) }
        }
    }

    fun setDebugModeNoTrigger(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateConfig { it.copy(debugModeNoTrigger = enabled) }
        }
    }

    fun updateThresholds(
        pitch: Float? = null,
        yaw: Float? = null,
        roll: Float? = null,
        eyeBlink: Float? = null,
        mouthOpen: Float? = null,
        smile: Float? = null
    ) {
        viewModelScope.launch {
            repository.updateConfig { cur ->
                cur.copy(
                    pitchThreshold = pitch ?: cur.pitchThreshold,
                    yawThreshold = yaw ?: cur.yawThreshold,
                    rollThreshold = roll ?: cur.rollThreshold,
                    eyeBlinkThreshold = eyeBlink ?: cur.eyeBlinkThreshold,
                    mouthOpenThreshold = mouthOpen ?: cur.mouthOpenThreshold,
                    smileThreshold = smile ?: cur.smileThreshold
                )
            }
        }
    }

    fun calibratePitchThreshold(currentPitch: Float) {
        val target = (kotlin.math.abs(currentPitch) * 0.88f).coerceIn(7f, 32f)
        updateThresholds(pitch = target)
    }

    fun calibrateYawThreshold(currentYaw: Float) {
        val target = (kotlin.math.abs(currentYaw) * 0.88f).coerceIn(9f, 35f)
        updateThresholds(yaw = target)
    }

    fun calibrateGazeThreshold(currentGazeX: Float) {
        val target = (kotlin.math.abs(currentGazeX) * 0.85f).coerceIn(0.15f, 0.45f)
        updateGazeSettings(hThreshold = target)
    }

    fun calibrateGazeVerticalThreshold(currentGazeY: Float) {
        val target = (kotlin.math.abs(currentGazeY) * 0.85f).coerceIn(0.15f, 0.45f)
        updateGazeSettings(vThreshold = target)
    }

    fun updateGazeSettings(
        enabled: Boolean? = null,
        hThreshold: Float? = null,
        vThreshold: Float? = null,
        dwellMs: Long? = null
    ) {
        viewModelScope.launch {
            repository.updateConfig { cur ->
                cur.copy(
                    gazeTrackingEnabled = enabled ?: cur.gazeTrackingEnabled,
                    gazeHorizontalThreshold = hThreshold ?: cur.gazeHorizontalThreshold,
                    gazeVerticalThreshold = vThreshold ?: cur.gazeVerticalThreshold,
                    gazeDwellDurationMs = dwellMs ?: cur.gazeDwellDurationMs
                )
            }
        }
    }

    fun updateTiming(holdMs: Long? = null, cooldownMs: Long? = null, sensitivity: Float? = null) {
        viewModelScope.launch {
            repository.updateConfig { cur ->
                cur.copy(
                    holdDurationMs = holdMs ?: cur.holdDurationMs,
                    cooldownMs = cooldownMs ?: cur.cooldownMs,
                    sensitivity = sensitivity ?: cur.sensitivity
                )
            }
        }
    }

    fun updatePerformanceOptions(frameSkipRate: Int? = null, resolutionTier: String? = null) {
        viewModelScope.launch {
            repository.updateConfig { cur ->
                cur.copy(
                    frameSkipRate = frameSkipRate ?: cur.frameSkipRate,
                    resolutionTier = resolutionTier ?: cur.resolutionTier
                )
            }
        }
    }

    fun updateFeedback(haptic: Boolean? = null, sound: Boolean? = null) {
        viewModelScope.launch {
            repository.updateConfig { cur ->
                cur.copy(
                    hapticFeedback = haptic ?: cur.hapticFeedback,
                    soundFeedback = sound ?: cur.soundFeedback
                )
            }
        }
    }

    fun updateEcoOptions(autoIdleSleep: Boolean? = null, screenOffPause: Boolean? = null) {
        viewModelScope.launch {
            repository.updateConfig { cur ->
                cur.copy(
                    autoIdleSleep = autoIdleSleep ?: cur.autoIdleSleep,
                    screenOffPause = screenOffPause ?: cur.screenOffPause
                )
            }
        }
    }

    fun updateOverlayAppearance(alpha: Float? = null, showPreview: Boolean? = null) {
        viewModelScope.launch {
            repository.updateConfig { cur ->
                cur.copy(
                    overlayAlpha = alpha ?: cur.overlayAlpha,
                    showCameraPreview = showPreview ?: cur.showCameraPreview
                )
            }
        }
    }

    fun toggleBinding(gesture: TriggerGesture, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleBinding(gesture, isEnabled)
        }
    }

    fun updateBindingAction(gesture: TriggerGesture, action: ActionType) {
        viewModelScope.launch {
            repository.updateBindingAction(gesture, action)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            repository.resetToDefaults()
        }
    }

    fun testSimulateAction(action: ActionType) {
        GestureAccessibilityService.instance?.performAction(action)
    }

    companion object {
        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun openOverlaySettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
