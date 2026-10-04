package com.example.model

data class RecognitionResult(
    // 头部姿态角度 (度)
    val headPitch: Float = 0f, // 仰俯 (+仰头, -低头)
    val headYaw: Float = 0f,   // 偏航 (+右转, -左转)
    val headRoll: Float = 0f,  // 倾斜 (+右歪, -左歪)

    // 眼睛与嘴巴
    val leftEyeOpenProb: Float = 1.0f,
    val rightEyeOpenProb: Float = 1.0f,
    val mouthOpenRatio: Float = 0f,
    val smilingProb: Float = 0f,

    // 眼动视线注视估计 (Gaze Offset)
    val gazeOffsetX: Float = 0f, // -1.0(向左看) ~ 0(居中) ~ +1.0(向右看)
    val gazeOffsetY: Float = 0f, // -1.0(向上看) ~ 0(居中) ~ +1.0(向下看)
    val isGazeTracked: Boolean = false,

    // 手势识别
    val handGesture: TriggerGesture? = null,
    val handConfidence: Float = 0f,

    // 当前活跃候选触发手势
    val candidateGesture: TriggerGesture? = null,
    val triggerHoldProgress: Float = 0f, // 0.0 ~ 1.0 (蓄力进度)

    // 最近一次成功触发的手势与动作
    val lastFiredGesture: TriggerGesture? = null,
    val lastFiredAction: ActionType? = null,
    val lastFiredTimestamp: Long = 0L,

    // 状态
    val isTrackingActive: Boolean = true,
    val statusMessage: String = "正在检测..."
)
