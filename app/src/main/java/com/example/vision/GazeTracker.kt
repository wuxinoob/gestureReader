package com.example.vision

import androidx.camera.core.ImageProxy
import com.example.model.TriggerGesture
import com.example.model.UserConfig
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import java.nio.ByteBuffer
import kotlin.math.abs

data class GazeResult(
    val isTracked: Boolean = false,
    val gazeOffsetX: Float = 0f, // -1.0 (向左看) ~ +1.0 (向右看)
    val gazeOffsetY: Float = 0f, // -1.0 (向上看) ~ +1.0 (向下看)
    val detectedGesture: TriggerGesture? = null,
    val confidence: Float = 0f
)

class GazeTracker {

    // 平滑滤波器参数 (过滤眼球生理微颤与微眼跳 Saccade)
    private var smoothedOffsetX = 0f
    private var smoothedOffsetY = 0f
    private val alpha = 0.38f

    fun estimateGaze(
        face: Face,
        image: ImageProxy,
        config: UserConfig,
        orientationTracker: DeviceOrientationTracker? = null,
        rotationDegrees: Int = 0
    ): GazeResult {
        if (!config.gazeTrackingEnabled) {
            return GazeResult(isTracked = false)
        }

        val leftEyeLandmark = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEyeLandmark = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

        if (leftEyeLandmark == null || rightEyeLandmark == null) {
            return GazeResult(isTracked = false)
        }

        // 双眼睁开度过低时无法可靠跟踪眼球 (眨眼或闭眼阶段)
        val leftOpen = face.leftEyeOpenProbability ?: 1.0f
        val rightOpen = face.rightEyeOpenProbability ?: 1.0f
        if (leftOpen < 0.32f || rightOpen < 0.32f) {
            return GazeResult(isTracked = false)
        }

        val planes = image.planes
        if (planes.isEmpty()) return GazeResult(isTracked = false)

        val rawW = image.width
        val rawH = image.height
        val yPlane = planes[0]
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride

        // 依据旋转角度计算 ML Kit 正向空间 (upright) 尺寸
        val isSwapped = (rotationDegrees == 90 || rotationDegrees == 270)
        val uprightW = if (isSwapped) rawH else rawW
        val uprightH = if (isSwapped) rawW else rawH

        // 1. 将 ML Kit 正向坐标准确反投回原始 YUV 字节缓冲区空间
        fun mapUprightToRaw(uX: Float, uY: Float): Pair<Int, Int> {
            val cx = uX.coerceIn(0f, uprightW.toFloat() - 1f)
            val cy = uY.coerceIn(0f, uprightH.toFloat() - 1f)
            return when (rotationDegrees) {
                90 -> Pair(cy.toInt().coerceIn(0, rawW - 1), (rawH - 1 - cx.toInt()).coerceIn(0, rawH - 1))
                180 -> Pair((rawW - 1 - cx.toInt()).coerceIn(0, rawW - 1), (rawH - 1 - cy.toInt()).coerceIn(0, rawH - 1))
                270 -> Pair((rawW - 1 - cy.toInt()).coerceIn(0, rawW - 1), cx.toInt().coerceIn(0, rawH - 1))
                else -> Pair(cx.toInt().coerceIn(0, rawW - 1), cy.toInt().coerceIn(0, rawH - 1))
            }
        }

        // 2. 将在原始缓冲区计算的瞳孔位移向量转换回屏幕视口空间，并叠加前置自拍镜像校准
        fun mapRawDeltaToUpright(dRawX: Float, dRawY: Float): Pair<Float, Float> {
            val (uDx, uDy) = when (rotationDegrees) {
                90 -> Pair(-dRawY, dRawX)
                180 -> Pair(-dRawX, -dRawY)
                270 -> Pair(dRawY, -dRawX)
                else -> Pair(dRawX, dRawY)
            }
            // 前置摄像头镜像校准：面向屏幕向右看时，自拍画面中瞳孔向左运动，需做水平镜像取反
            return Pair(-uDx, uDy)
        }

        val rawLeftCenter = mapUprightToRaw(leftEyeLandmark.x, leftEyeLandmark.y)
        val rawRightCenter = mapUprightToRaw(rightEyeLandmark.x, rightEyeLandmark.y)

        // 采样窗口大小依据人脸框动态确定
        val boxWidth = if (isSwapped) face.boundingBox.height() else face.boundingBox.width()
        val eyeBoxHalfW = (boxWidth * 0.08f).toInt().coerceIn(8, 36)
        val eyeBoxHalfH = (boxWidth * 0.05f).toInt().coerceIn(6, 24)

        val leftEyeDeltaRaw = findPupilOffset(
            centerX = rawLeftCenter.first,
            centerY = rawLeftCenter.second,
            halfW = eyeBoxHalfW,
            halfH = eyeBoxHalfH,
            imgW = rawW,
            imgH = rawH,
            buffer = buffer,
            rowStride = rowStride,
            pixelStride = pixelStride
        )

        val rightEyeDeltaRaw = findPupilOffset(
            centerX = rawRightCenter.first,
            centerY = rawRightCenter.second,
            halfW = eyeBoxHalfW,
            halfH = eyeBoxHalfH,
            imgW = rawW,
            imgH = rawH,
            buffer = buffer,
            rowStride = rowStride,
            pixelStride = pixelStride
        )

        // 映射回正立空间
        val leftEyeDelta = mapRawDeltaToUpright(leftEyeDeltaRaw.first, leftEyeDeltaRaw.second)
        val rightEyeDelta = mapRawDeltaToUpright(rightEyeDeltaRaw.first, rightEyeDeltaRaw.second)

        // 双眼融合计算平均注视偏离
        val rawX = (leftEyeDelta.first + rightEyeDelta.first) / 2f
        val rawY = (leftEyeDelta.second + rightEyeDelta.second) / 2f

        // 人体工学生理增益放大 (Gain factor 3.2x)：
        // 眼睛在眼眶中的物理移动行程较小，通过适度放大映射为标准 -1.0 ~ +1.0 范围，
        // 确保用户自然移向屏幕边缘时即可轻松突破阈值，无需费力翻白眼
        val amplifiedX = (rawX * 3.2f).coerceIn(-1f, 1f)
        val amplifiedY = (rawY * 2.8f).coerceIn(-1f, 1f)

        // EMA 平滑滤波
        smoothedOffsetX = smoothedOffsetX * (1f - alpha) + amplifiedX * alpha
        smoothedOffsetY = smoothedOffsetY * (1f - alpha) + amplifiedY * alpha

        // 依据当前设备物理方向 (横屏/竖屏) 将眼动向量转换到当前屏幕视口坐标空间
        val (screenGazeX, screenGazeY) = if (orientationTracker != null) {
            orientationTracker.compensateGaze(smoothedOffsetX, smoothedOffsetY)
        } else {
            smoothedOffsetX to smoothedOffsetY
        }

        // 依据屏幕长宽比自适应计算横纵触发阈值
        val (effHThreshold, effVThreshold) = if (orientationTracker != null) {
            orientationTracker.getAdaptiveGazeThresholds(
                config.effectiveGazeHThreshold(),
                config.effectiveGazeVThreshold()
            )
        } else {
            config.effectiveGazeHThreshold() to config.effectiveGazeVThreshold()
        }

        // 视线相对零点标定校准 (以用户当前舒适坐姿的自然注视点为 0, 0 绝对中心)
        val relGazeX = (screenGazeX - config.baselineGazeX).coerceIn(-1f, 1f)
        val relGazeY = (screenGazeY - config.baselineGazeY).coerceIn(-1f, 1f)

        var gesture: TriggerGesture? = null

        when {
            // 视线看屏幕右下角驻留 (读完本页翻下一页)
            relGazeX > effHThreshold && relGazeY > effVThreshold -> {
                gesture = TriggerGesture.GAZE_DWELL_CORNER
            }
            // 视线看屏幕右侧边缘 (翻下一页)
            relGazeX > effHThreshold -> {
                gesture = TriggerGesture.GAZE_LOOK_RIGHT
            }
            // 视线看屏幕左侧边缘 (翻上一页)
            relGazeX < -effHThreshold -> {
                gesture = TriggerGesture.GAZE_LOOK_LEFT
            }
            // 视线向下看至页面最底端
            relGazeY > effVThreshold -> {
                gesture = TriggerGesture.GAZE_LOOK_DOWN
            }
            // 视线向上看至页面最顶端
            relGazeY < -effVThreshold -> {
                gesture = TriggerGesture.GAZE_LOOK_UP
            }
        }

        return GazeResult(
            isTracked = true,
            gazeOffsetX = relGazeX,
            gazeOffsetY = relGazeY,
            detectedGesture = gesture,
            confidence = 0.88f
        )
    }

    /**
     * 在眼睛局部区域利用灰度极小值与加权质心精确定位虹膜/瞳孔中心 (Pupil Dark Center)
     * 返回相对于眼眶中心的归一化偏移量 (-1.0 ~ +1.0)
     */
    private fun findPupilOffset(
        centerX: Int,
        centerY: Int,
        halfW: Int,
        halfH: Int,
        imgW: Int,
        imgH: Int,
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int
    ): Pair<Float, Float> {
        val minX = (centerX - halfW).coerceIn(0, imgW - 1)
        val maxX = (centerX + halfW).coerceIn(0, imgW - 1)
        val minY = (centerY - halfH).coerceIn(0, imgH - 1)
        val maxY = (centerY + halfH).coerceIn(0, imgH - 1)

        var minLuma = 255
        // 1. 查找眼眶最低亮度 (黑眼珠/瞳孔深色心)
        for (y in minY..maxY step 2) {
            val rowOffset = y * rowStride
            for (x in minX..maxX step 2) {
                val idx = rowOffset + x * pixelStride
                if (idx < buffer.limit()) {
                    val luma = buffer.get(idx).toInt() and 0xFF
                    if (luma < minLuma) {
                        minLuma = luma
                    }
                }
            }
        }

        // 2. 聚类最暗的像素区域计算瞳孔加权质心
        var darkSumX = 0L
        var darkSumY = 0L
        var darkCount = 0
        val threshold = (minLuma + 22).coerceAtMost(160)

        for (y in minY..maxY step 2) {
            val rowOffset = y * rowStride
            for (x in minX..maxX step 2) {
                val idx = rowOffset + x * pixelStride
                if (idx < buffer.limit()) {
                    val luma = buffer.get(idx).toInt() and 0xFF
                    if (luma <= threshold) {
                        // 越暗权重越高
                        val weight = (threshold - luma + 1)
                        darkSumX += x * weight
                        darkSumY += y * weight
                        darkCount += weight
                    }
                }
            }
        }

        if (darkCount == 0 || halfW == 0 || halfH == 0) {
            return Pair(0f, 0f)
        }

        val pupilX = darkSumX.toFloat() / darkCount
        val pupilY = darkSumY.toFloat() / darkCount

        val offsetX = ((pupilX - centerX) / halfW).coerceIn(-1f, 1f)
        val offsetY = ((pupilY - centerY) / halfH).coerceIn(-1f, 1f)

        return Pair(offsetX, offsetY)
    }
}
