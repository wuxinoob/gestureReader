package com.example.model

data class PerformanceMetrics(
    val fps: Float = 0f,
    val inferenceTimeMs: Long = 0L,
    val totalFrameTimeMs: Long = 0L,
    val droppedFramesCount: Int = 0,
    val faceConfidence: Float = 0f,
    val isFaceTracked: Boolean = false,
    val isHandTracked: Boolean = false,
    val cpuLoadEstimate: Float = 0f, // 0.0 ~ 1.0
    val framesProcessedCount: Long = 0L
)
