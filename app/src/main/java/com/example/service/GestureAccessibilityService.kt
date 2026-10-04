package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.model.ActionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GestureAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceBound.value = true
        Log.i(TAG, "GestureAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要阻断处理无障碍事件，本服务重点在无障碍手势模拟与辅助分发
    }

    override fun onInterrupt() {
        Log.w(TAG, "GestureAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceBound.value = false
    }

    fun performAction(action: ActionType): Boolean {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f

        return when (action) {
            ActionType.SWIPE_UP -> {
                // 向上滑动 (内容下滚 / 翻至下一页)
                simulateSwipe(
                    startX = centerX,
                    startY = height * 0.75f,
                    endX = centerX,
                    endY = height * 0.25f,
                    duration = 260L
                )
            }
            ActionType.SWIPE_DOWN -> {
                // 向下滑动 (内容上滚 / 翻至上一页)
                simulateSwipe(
                    startX = centerX,
                    startY = height * 0.25f,
                    endX = centerX,
                    endY = height * 0.75f,
                    duration = 260L
                )
            }
            ActionType.SWIPE_LEFT -> {
                // 向左横划 (下一页)
                simulateSwipe(
                    startX = width * 0.85f,
                    startY = centerY,
                    endX = width * 0.15f,
                    endY = centerY,
                    duration = 220L
                )
            }
            ActionType.SWIPE_RIGHT -> {
                // 向右横划 (上一页)
                simulateSwipe(
                    startX = width * 0.15f,
                    startY = centerY,
                    endX = width * 0.85f,
                    endY = centerY,
                    duration = 220L
                )
            }
            ActionType.CLICK_CENTER -> {
                // 屏幕中心轻触
                simulateClick(centerX, centerY)
            }
            ActionType.BACK_KEY -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            ActionType.HOME_KEY -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            ActionType.SCROLL_DOWN_SLOW -> {
                // 慢速向下微滚
                simulateSwipe(
                    startX = centerX,
                    startY = centerY + 180f,
                    endX = centerX,
                    endY = centerY - 180f,
                    duration = 380L
                )
            }
            ActionType.SCROLL_UP_SLOW -> {
                // 慢速向上微滚
                simulateSwipe(
                    startX = centerX,
                    startY = centerY - 180f,
                    endX = centerX,
                    endY = centerY + 180f,
                    duration = 380L
                )
            }
            ActionType.TOGGLE_TRACKING, ActionType.NONE -> {
                false
            }
        }
    }

    private fun simulateSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    private fun simulateClick(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 50L))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    companion object {
        private const val TAG = "GestureAccessibility"
        var instance: GestureAccessibilityService? = null
            private set

        private val _isServiceBound = MutableStateFlow(false)
        val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

        fun isAccessibilityEnabled(context: Context): Boolean {
            // 1. 若当前服务实例活跃存活，百分之百已开启无障碍授权
            if (instance != null) return true

            // 2. 使用官方系统 AccessibilityManager 查询已授权服务列表 (精准可靠且不受系统设置字符串格式变动影响)
            try {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
                val enabledServices = am?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                if (enabledServices != null) {
                    val myPackage = context.packageName
                    for (info in enabledServices) {
                        val sInfo = info.resolveInfo?.serviceInfo ?: continue
                        if (sInfo.packageName == myPackage && sInfo.name.contains("GestureAccessibilityService")) {
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking AccessibilityManager: ${e.message}")
            }

            // 3. 兼容兜底方案：通过 Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES 查询
            val packageName = context.packageName
            try {
                val settingValue = Settings.Secure.getString(
                    context.applicationContext.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                if (settingValue != null) {
                    val splitter = TextUtils.SimpleStringSplitter(':')
                    splitter.setString(settingValue)
                    while (splitter.hasNext()) {
                        val accessService = splitter.next()
                        if (accessService.contains(packageName, ignoreCase = true) &&
                            accessService.contains("GestureAccessibilityService", ignoreCase = true)
                        ) {
                            return true
                        }
                    }
                }
            } catch (_: Exception) {}

            return false
        }
    }
}
