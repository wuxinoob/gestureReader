package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ActionType
import com.example.model.ConfigPreset
import com.example.model.TriggerGesture
import com.example.model.UserConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GestureRepository(
    private val context: Context,
    private val gestureDao: GestureDao
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gesture_reader_prefs", Context.MODE_PRIVATE)

    private val _configState = MutableStateFlow(loadConfigFromPrefs())
    val configState: StateFlow<UserConfig> = _configState.asStateFlow()

    val allBindings: Flow<List<GestureBindingEntity>> = gestureDao.getAllBindings()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val currentList = allBindings.first()
            if (currentList.isEmpty()) {
                AppDatabase.populateDefaultBindings(gestureDao)
            } else {
                // 安全迁移：防止旧数据库中的 toggle_tracking 或 back_key 导致启动时误触
                currentList.forEach { entity ->
                    if (entity.actionId == "toggle_tracking") {
                        gestureDao.updateAction(entity.gestureId, "scroll_down_slow")
                    }
                    if (entity.actionId == "back_key" && entity.gestureId.startsWith("hand_")) {
                        gestureDao.updateAction(entity.gestureId, "click_center")
                        gestureDao.toggleBinding(entity.gestureId, false)
                    }
                }
            }
        }
    }

    private fun loadConfigFromPrefs(): UserConfig {
        return UserConfig(
            pitchThreshold = prefs.getFloat("pitchThreshold", 16f),
            yawThreshold = prefs.getFloat("yawThreshold", 20f),
            rollThreshold = prefs.getFloat("rollThreshold", 18f),
            baselinePitch = prefs.getFloat("baselinePitch", 0f),
            baselineYaw = prefs.getFloat("baselineYaw", 0f),
            baselineRoll = prefs.getFloat("baselineRoll", 0f),
            baselineGazeX = prefs.getFloat("baselineGazeX", 0f),
            baselineGazeY = prefs.getFloat("baselineGazeY", 0f),
            activePreset = prefs.getString("activePreset", ConfigPreset.DEFAULT.id) ?: ConfigPreset.DEFAULT.id,
            eyeBlinkThreshold = prefs.getFloat("eyeBlinkThreshold", 0.35f),
            mouthOpenThreshold = prefs.getFloat("mouthOpenThreshold", 0.28f),
            smileThreshold = prefs.getFloat("smileThreshold", 0.65f),
            holdDurationMs = prefs.getLong("holdDurationMs", 260L),
            cooldownMs = prefs.getLong("cooldownMs", 850L),
            sensitivity = prefs.getFloat("sensitivity", 1.0f),
            frameSkipRate = prefs.getInt("frameSkipRate", 2),
            resolutionTier = prefs.getString("resolutionTier", "LOW") ?: "LOW",
            hapticFeedback = prefs.getBoolean("hapticFeedback", true),
            soundFeedback = prefs.getBoolean("soundFeedback", false),
            overlayAlpha = prefs.getFloat("overlayAlpha", 0.92f),
            showCameraPreview = prefs.getBoolean("showCameraPreview", false),
            showPerformanceHud = prefs.getBoolean("showPerformanceHud", true),
            isPaused = prefs.getBoolean("isPaused", false),
            debugModeNoTrigger = prefs.getBoolean("debugModeNoTrigger", false),
            isDarkMode = prefs.getBoolean("isDarkMode", false),
            autoIdleSleep = prefs.getBoolean("autoIdleSleep", true),
            screenOffPause = prefs.getBoolean("screenOffPause", true)
        )
    }

    suspend fun updateConfig(update: (UserConfig) -> UserConfig) {
        val newConfig = update(_configState.value)
        _configState.value = newConfig
        withContext(Dispatchers.IO) {
            prefs.edit().apply {
                putFloat("pitchThreshold", newConfig.pitchThreshold)
                putFloat("yawThreshold", newConfig.yawThreshold)
                putFloat("rollThreshold", newConfig.rollThreshold)
                putFloat("baselinePitch", newConfig.baselinePitch)
                putFloat("baselineYaw", newConfig.baselineYaw)
                putFloat("baselineRoll", newConfig.baselineRoll)
                putFloat("baselineGazeX", newConfig.baselineGazeX)
                putFloat("baselineGazeY", newConfig.baselineGazeY)
                putString("activePreset", newConfig.activePreset)
                putFloat("eyeBlinkThreshold", newConfig.eyeBlinkThreshold)
                putFloat("mouthOpenThreshold", newConfig.mouthOpenThreshold)
                putFloat("smileThreshold", newConfig.smileThreshold)
                putLong("holdDurationMs", newConfig.holdDurationMs)
                putLong("cooldownMs", newConfig.cooldownMs)
                putFloat("sensitivity", newConfig.sensitivity)
                putInt("frameSkipRate", newConfig.frameSkipRate)
                putString("resolutionTier", newConfig.resolutionTier)
                putBoolean("hapticFeedback", newConfig.hapticFeedback)
                putBoolean("soundFeedback", newConfig.soundFeedback)
                putFloat("overlayAlpha", newConfig.overlayAlpha)
                putBoolean("showCameraPreview", newConfig.showCameraPreview)
                putBoolean("showPerformanceHud", newConfig.showPerformanceHud)
                putBoolean("isPaused", newConfig.isPaused)
                putBoolean("debugModeNoTrigger", newConfig.debugModeNoTrigger)
                putBoolean("isDarkMode", newConfig.isDarkMode)
                putBoolean("autoIdleSleep", newConfig.autoIdleSleep)
                putBoolean("screenOffPause", newConfig.screenOffPause)
                apply()
            }
        }
    }

    suspend fun toggleBinding(gesture: TriggerGesture, isEnabled: Boolean) {
        withContext(Dispatchers.IO) {
            gestureDao.toggleBinding(gesture.id, isEnabled)
        }
    }

    suspend fun updateBindingAction(gesture: TriggerGesture, action: ActionType) {
        withContext(Dispatchers.IO) {
            gestureDao.updateAction(gesture.id, action.id)
        }
    }

    suspend fun resetToDefaults() {
        withContext(Dispatchers.IO) {
            AppDatabase.populateDefaultBindings(gestureDao)
            updateConfig { UserConfig() }
        }
    }
}
