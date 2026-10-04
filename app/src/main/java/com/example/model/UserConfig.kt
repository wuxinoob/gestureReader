package com.example.model

enum class ConfigPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconEmoji: String
) {
    DEFAULT("DEFAULT", "标准均衡", "日常通用，防误触与响应速度均衡", "⚖️"),
    BED("BED", "舒适卧姿", "适于靠床/仰躺，大俯仰容差，微动即翻", "🛋️"),
    DESK("DESK", "桌面支架", "适于支架/办公桌，高精度视线注视优先", "🖥️"),
    COMMUTE("COMMUTE", "通勤防抖", "适于地铁/公交，深度滤波防颠簸误触", "🚌"),
    SPEED_READER("SPEED_READER", "极速连读", "超短延迟 160ms，翻页行云流水", "⚡")
}

data class UserConfig(
    // 头部姿态角度阈值 (度)
    val pitchThreshold: Float = 16f,
    val yawThreshold: Float = 20f,
    val rollThreshold: Float = 18f,

    // 姿态与视线基准零点归零偏移 (以用户当前舒适姿态与视线为中心原点 0, 0)
    val baselinePitch: Float = 0f,
    val baselineYaw: Float = 0f,
    val baselineRoll: Float = 0f,
    val baselineGazeX: Float = 0f,
    val baselineGazeY: Float = 0f,

    // 当前场景配置预设
    val activePreset: String = ConfigPreset.DEFAULT.id,

    // 面部表情阈值
    val eyeBlinkThreshold: Float = 0.35f,
    val mouthOpenThreshold: Float = 0.28f,
    val smileThreshold: Float = 0.65f,

    // 眼动视线注视阈值 (Gaze Thresholds)
    val gazeTrackingEnabled: Boolean = true,
    val gazeHorizontalThreshold: Float = 0.22f, // 水平视线偏移量 (0.10 ~ 0.40)
    val gazeVerticalThreshold: Float = 0.25f,   // 垂直视线下看偏移量 (0.12 ~ 0.45)
    val gazeDwellDurationMs: Long = 650L,       // 边缘驻留触发时长 (300ms ~ 2000ms, 防误触)

    // 触发保持时间与冷却时间 (毫秒)
    val holdDurationMs: Long = 260L,
    val cooldownMs: Long = 850L,

    // 灵敏度综合倍率 (0.5x ~ 2.0x)
    val sensitivity: Float = 1.0f,

    // 性能优化配置 (极低功耗阅读优化: 15FPS低采样与轻量分辨率)
    val frameSkipRate: Int = 2, // 默认隔1帧处理1帧 (约15 FPS)，对于阅读翻页极其平滑且功耗立降 50%
    val resolutionTier: String = "LOW", // 默认 480x360 快速分析，大幅降低 NPU/CPU 算力与内存带宽发热

    // 反馈配置
    val hapticFeedback: Boolean = true,
    val soundFeedback: Boolean = false,

    // 悬浮窗配置
    val overlayAlpha: Float = 0.92f,
    val showCameraPreview: Boolean = false, // 默认日常阅读不绘制摄像头画面，沉浸无遮挡
    val showPerformanceHud: Boolean = true,
    val isPaused: Boolean = false,
    val debugModeNoTrigger: Boolean = false, // 阈值调试模式：静默监控数值与进度条，不向系统分发翻页或按键动作
    val isDarkMode: Boolean = false, // 主题模式：支持深色模式，默认浅色 (Default Light Mode)
    val autoIdleSleep: Boolean = true, // 智能离镜休眠节电：无人脸3秒后降频至 2.5 FPS 哨兵模式，降低 70% 耗电
    val screenOffPause: Boolean = true // 息屏自动释放硬件：锁屏后立即注销摄像头，待机实现 0% 耗电
) {
    // 经灵敏度缩放后的有效仰俯角阈值
    fun effectivePitchThreshold(): Float = (pitchThreshold / sensitivity).coerceIn(6f, 45f)
    fun effectiveYawThreshold(): Float = (yawThreshold / sensitivity).coerceIn(8f, 50f)
    fun effectiveRollThreshold(): Float = (rollThreshold / sensitivity).coerceIn(8f, 50f)
    fun effectiveHoldDurationMs(): Long = (holdDurationMs / sensitivity).toLong().coerceIn(100L, 1500L)
    fun effectiveGazeDwellMs(): Long = (gazeDwellDurationMs / sensitivity).toLong().coerceIn(250L, 2500L)
    fun effectiveGazeHThreshold(): Float = (gazeHorizontalThreshold / sensitivity).coerceIn(0.08f, 0.45f)
    fun effectiveGazeVThreshold(): Float = (gazeVerticalThreshold / sensitivity).coerceIn(0.10f, 0.50f)

    fun withPreset(preset: ConfigPreset): UserConfig = when (preset) {
        ConfigPreset.DEFAULT -> copy(
            activePreset = preset.id,
            pitchThreshold = 16f,
            yawThreshold = 20f,
            rollThreshold = 18f,
            holdDurationMs = 260L,
            cooldownMs = 850L,
            sensitivity = 1.0f,
            gazeTrackingEnabled = true
        )
        ConfigPreset.BED -> copy(
            activePreset = preset.id,
            pitchThreshold = 22f,
            yawThreshold = 18f,
            rollThreshold = 16f,
            holdDurationMs = 220L,
            cooldownMs = 800L,
            sensitivity = 1.15f,
            gazeTrackingEnabled = true
        )
        ConfigPreset.DESK -> copy(
            activePreset = preset.id,
            pitchThreshold = 14f,
            yawThreshold = 16f,
            rollThreshold = 15f,
            holdDurationMs = 280L,
            cooldownMs = 900L,
            sensitivity = 1.0f,
            gazeTrackingEnabled = true
        )
        ConfigPreset.COMMUTE -> copy(
            activePreset = preset.id,
            pitchThreshold = 20f,
            yawThreshold = 24f,
            rollThreshold = 22f,
            holdDurationMs = 420L,
            cooldownMs = 1200L,
            sensitivity = 0.85f,
            gazeTrackingEnabled = false
        )
        ConfigPreset.SPEED_READER -> copy(
            activePreset = preset.id,
            pitchThreshold = 13f,
            yawThreshold = 15f,
            rollThreshold = 14f,
            holdDurationMs = 160L,
            cooldownMs = 600L,
            sensitivity = 1.35f,
            gazeTrackingEnabled = true
        )
    }
}
