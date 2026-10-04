package com.example.vision

import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.example.model.TriggerGesture
import java.nio.ByteBuffer
import kotlin.math.abs

data class HandDetectionResult(
    val hasHand: Boolean = false,
    val gesture: TriggerGesture? = null,
    val confidence: Float = 0f,
    val centroidX: Float = 0.5f,
    val centroidY: Float = 0.5f,
    val areaFraction: Float = 0f
)

class HandGestureAnalyzer {

    // 历史位置轨迹 (时间戳, 屏幕坐标X, 屏幕坐标Y)
    private val motionHistory = ArrayDeque<Triple<Long, Float, Float>>()
    private val maxHistoryDurationMs = 450L
    private var lastTriggeredSwipeTimestamp = 0L

    fun analyze(
        image: ImageProxy,
        rotationDegrees: Int = 0,
        faceBox: Rect? = null
    ): HandDetectionResult {
        return try {
            val planes = image.planes
            if (planes.isEmpty()) return HandDetectionResult(hasHand = false)

            val yPlane = planes[0]
            val yBuffer: ByteBuffer = yPlane.buffer
            val width = image.width
            val height = image.height
            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride

            val hasUv = planes.size >= 3
            val uPlane = if (hasUv) planes[1] else null
            val vPlane = if (hasUv) planes[2] else null
            val uBuffer = uPlane?.buffer
            val vBuffer = vPlane?.buffer
            val uRowStride = uPlane?.rowStride ?: 0
            val uPixelStride = uPlane?.pixelStride ?: 1
            val vRowStride = vPlane?.rowStride ?: 0
            val vPixelStride = vPlane?.pixelStride ?: 1

            // 步长低采样分析 (步长8，保证处理耗时在 2ms 以内)
            val step = 8
            var count = 0
            var sumX = 0L
            var sumY = 0L
            var minX = width
            var maxX = 0
            var minY = height
            var maxY = 0

            yBuffer.rewind()
            uBuffer?.rewind()
            vBuffer?.rewind()

            for (y in 0 until height step step) {
                val yRowOffset = y * yRowStride
                val uvY = y / 2
                val uRowOffset = uvY * uRowStride
                val vRowOffset = uvY * vRowStride

                for (x in 0 until width step step) {
                    val yIndex = yRowOffset + x * yPixelStride
                    if (yIndex < yBuffer.limit()) {
                        val yVal = yBuffer.get(yIndex).toInt() and 0xFF

                        // 基于 YUV420 双通道色度进行高精度肤色过滤 (杜绝白墙、桌面、衣物误判)
                        val isSkin = if (hasUv && uBuffer != null && vBuffer != null) {
                            val uvX = x / 2
                            val uIndex = uRowOffset + uvX * uPixelStride
                            val vIndex = vRowOffset + uvX * vPixelStride
                            if (uIndex < uBuffer.limit() && vIndex < vBuffer.limit()) {
                                val uVal = uBuffer.get(uIndex).toInt() and 0xFF
                                val vVal = vBuffer.get(vIndex).toInt() and 0xFF
                                (yVal in 45..245) && (vVal in 128..180) && (uVal in 75..135) && (vVal > uVal)
                            } else {
                                yVal in 70..230
                            }
                        } else {
                            yVal in 70..230
                        }

                        if (isSkin) {
                            // 排除人脸与头部区域，仅将下部和侧边的手部/手臂纳入分析
                            if (faceBox != null) {
                                // 适度扩大脸部边距
                                val marginX = faceBox.width() / 4
                                val marginY = faceBox.height() / 4
                                if (x >= (faceBox.left - marginX) && x <= (faceBox.right + marginX) &&
                                    y >= (faceBox.top - marginY) && y <= (faceBox.bottom + marginY)
                                ) {
                                    continue
                                }
                            }

                            count++
                            sumX += x
                            sumY += y
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                    }
                }
            }

            val totalSampled = (height / step) * (width / step)
            val coverage = if (totalSampled > 0) count.toFloat() / totalSampled else 0f

            // 手部像素覆盖在合理范围 (0.015 ~ 0.55)
            if (count < 12 || coverage < 0.015f || coverage > 0.55f) {
                return HandDetectionResult(hasHand = false)
            }

            val rawCentroidX = (sumX.toFloat() / count) / width
            val rawCentroidY = (sumY.toFloat() / count) / height
            val rawBoundingWidth = (maxX - minX).toFloat() / width
            val rawBoundingHeight = (maxY - minY).toFloat() / height

            // 将相机原始坐标系映射为当前屏幕视口坐标空间 (处理旋转与前置镜像)
            val (screenX, screenY) = mapRawToScreen(rawCentroidX, rawCentroidY, rotationDegrees)
            val (screenBw, screenBh) = if (rotationDegrees == 90 || rotationDegrees == 270) {
                rawBoundingHeight to rawBoundingWidth
            } else {
                rawBoundingWidth to rawBoundingHeight
            }

            val now = System.currentTimeMillis()
            motionHistory.addLast(Triple(now, screenX, screenY))
            while (motionHistory.isNotEmpty() && (now - motionHistory.first().first > maxHistoryDurationMs)) {
                motionHistory.removeFirst()
            }

            // 1. 动态挥手检测 (向左挥手 / 向右挥手 / 向上 / 向下)
            if (motionHistory.size >= 2 && (now - lastTriggeredSwipeTimestamp > 500L)) {
                val oldest = motionHistory.first()
                val newest = motionHistory.last()
                val dx = newest.second - oldest.second
                val dy = newest.third - oldest.third
                val dt = (newest.first - oldest.first).coerceAtLeast(1L) / 1000f
                val speedX = dx / dt
                val speedY = dy / dt

                // 水平挥手 (位移大于 14% 屏幕宽度，且水平速度占优)
                if (abs(dx) > 0.14f && abs(speedX) > 0.40f && abs(dx) > abs(dy) * 1.15f) {
                    lastTriggeredSwipeTimestamp = now
                    motionHistory.clear()
                    return if (dx > 0) {
                        HandDetectionResult(
                            hasHand = true,
                            gesture = TriggerGesture.HAND_SWIPE_RIGHT,
                            confidence = 0.90f,
                            centroidX = screenX,
                            centroidY = screenY,
                            areaFraction = coverage
                        )
                    } else {
                        HandDetectionResult(
                            hasHand = true,
                            gesture = TriggerGesture.HAND_SWIPE_LEFT,
                            confidence = 0.90f,
                            centroidX = screenX,
                            centroidY = screenY,
                            areaFraction = coverage
                        )
                    }
                }

                // 纵向挥动 / 上下指示
                if (abs(dy) > 0.16f && abs(speedY) > 0.45f && abs(dy) > abs(dx) * 1.15f) {
                    lastTriggeredSwipeTimestamp = now
                    motionHistory.clear()
                    return if (dy < 0) {
                        HandDetectionResult(
                            hasHand = true,
                            gesture = TriggerGesture.HAND_POINT_UP,
                            confidence = 0.88f,
                            centroidX = screenX,
                            centroidY = screenY,
                            areaFraction = coverage
                        )
                    } else {
                        HandDetectionResult(
                            hasHand = true,
                            gesture = TriggerGesture.HAND_POINT_DOWN,
                            confidence = 0.88f,
                            centroidX = screenX,
                            centroidY = screenY,
                            areaFraction = coverage
                        )
                    }
                }
            }

            // 2. 静态手势形状分类 (手部相对静止时)
            val aspectRatio = if (screenBw > 0.01f) screenBh / screenBw else 1.0f

            val staticGesture = when {
                // 掌心展开 (举掌)
                coverage in 0.08f..0.35f && aspectRatio in 0.75f..1.35f -> {
                    TriggerGesture.HAND_PALM
                }
                // 握拳 (紧凑，面积小)
                coverage in 0.03f..0.15f && aspectRatio in 0.80f..1.25f -> {
                    TriggerGesture.HAND_FIST
                }
                // 竖大拇指 (竖向修长)
                aspectRatio > 1.45f && coverage in 0.03f..0.22f -> {
                    TriggerGesture.HAND_THUMBS_UP
                }
                // V字剪刀手
                aspectRatio in 1.25f..1.55f && coverage in 0.04f..0.18f -> {
                    TriggerGesture.HAND_V_SIGN
                }
                else -> null
            }

            HandDetectionResult(
                hasHand = true,
                gesture = staticGesture,
                confidence = if (staticGesture != null) 0.85f else 0.5f,
                centroidX = screenX,
                centroidY = screenY,
                areaFraction = coverage
            )
        } catch (_: Exception) {
            HandDetectionResult(hasHand = false)
        }
    }

    private fun mapRawToScreen(rawNormX: Float, rawNormY: Float, rotationDegrees: Int): Pair<Float, Float> {
        val (uX, uY) = when (rotationDegrees) {
            90 -> Pair(rawNormY, 1f - rawNormX)
            180 -> Pair(1f - rawNormX, 1f - rawNormY)
            270 -> Pair(1f - rawNormY, rawNormX)
            else -> Pair(rawNormX, rawNormY)
        }
        // 前置自拍画面水平镜像校准：面向屏幕向右挥手对应屏幕正向右侧
        return Pair(1f - uX, uY)
    }

    fun reset() {
        motionHistory.clear()
        lastTriggeredSwipeTimestamp = 0L
    }
}
