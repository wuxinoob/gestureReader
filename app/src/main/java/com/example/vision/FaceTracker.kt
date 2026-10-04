package com.example.vision

import android.graphics.PointF
import com.example.model.RecognitionResult
import com.example.model.TriggerGesture
import com.example.model.UserConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.hypot

data class FaceRawData(
    val hasFace: Boolean = false,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val leftEyeOpenProb: Float = 1.0f,
    val rightEyeOpenProb: Float = 1.0f,
    val smilingProb: Float = 0f,
    val mouthOpenRatio: Float = 0f,
    val confidence: Float = 0f,
    val rawFace: Face? = null
)

class FaceTracker {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.18f)
        .enableTracking()
        .build()

    private val detector: FaceDetector? by lazy {
        try {
            FaceDetection.getClient(options)
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun detectFace(image: InputImage): FaceRawData = suspendCancellableCoroutine { continuation ->
        val det = detector
        if (det == null) {
            continuation.resume(FaceRawData(hasFace = false))
            return@suspendCancellableCoroutine
        }
        det.process(image)
            .addOnSuccessListener { faces ->
                if (!continuation.isActive) return@addOnSuccessListener
                if (faces.isEmpty()) {
                    continuation.resume(FaceRawData(hasFace = false))
                } else {
                    val face = faces[0]
                    val raw = extractFaceData(face)
                    continuation.resume(raw)
                }
            }
            .addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resume(FaceRawData(hasFace = false))
                }
            }
    }

    private fun extractFaceData(face: Face): FaceRawData {
        val pitch = face.headEulerAngleX // + 仰头, - 低头
        val yaw = face.headEulerAngleY   // + 向右偏转, - 向左偏转
        val roll = face.headEulerAngleZ  // + 向右倾斜, - 向左倾斜

        val leftEye = face.leftEyeOpenProbability ?: 1.0f
        val rightEye = face.rightEyeOpenProbability ?: 1.0f
        val smile = face.smilingProbability ?: 0.0f

        // 计算张嘴幅度
        val mouthRatio = calculateMouthOpenRatio(face)

        val trackingConfidence = if (face.trackingId != null) 0.95f else 0.70f

        return FaceRawData(
            hasFace = true,
            pitch = pitch,
            yaw = yaw,
            roll = roll,
            leftEyeOpenProb = leftEye,
            rightEyeOpenProb = rightEye,
            smilingProb = smile,
            mouthOpenRatio = mouthRatio,
            confidence = trackingConfidence,
            rawFace = face
        )
    }

    private fun calculateMouthOpenRatio(face: Face): Float {
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position

        if (mouthBottom != null && mouthLeft != null && mouthRight != null) {
            val mouthWidth = hypot((mouthRight.x - mouthLeft.x).toDouble(), (mouthRight.y - mouthLeft.y).toDouble()).toFloat()
            if (mouthWidth > 1f) {
                val mouthCenterY = (mouthLeft.y + mouthRight.y) / 2f
                val mouthHeight = kotlin.math.abs(mouthBottom.y - mouthCenterY)
                val ratio = mouthHeight / mouthWidth
                return ratio.coerceIn(0f, 1f)
            }
        }

        if (mouthBottom != null && noseBase != null) {
            val noseToMouth = kotlin.math.abs(mouthBottom.y - noseBase.y)
            val faceHeight = face.boundingBox.height().toFloat()
            if (faceHeight > 10f) {
                val normalized = (noseToMouth / faceHeight) - 0.22f
                return (normalized * 2.5f).coerceIn(0f, 1f)
            }
        }
        return 0f
    }

    fun close() {
        try {
            detector?.close()
        } catch (_: Exception) {}
    }
}
