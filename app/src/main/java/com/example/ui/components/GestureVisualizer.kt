package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GestureCategory
import com.example.model.RecognitionResult
import com.example.model.UserConfig
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError

@Composable
fun GestureVisualizer(
    result: RecognitionResult,
    config: UserConfig? = null,
    category: GestureCategory? = null,
    onCalibratePitch: ((Float) -> Unit)? = null,
    onCalibrateYaw: ((Float) -> Unit)? = null,
    onCalibrateGaze: ((Float) -> Unit)? = null,
    onCalibrateGazeVertical: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var activeCategory by remember(category) {
        mutableStateOf(category ?: GestureCategory.HEAD)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = result.triggerHoldProgress,
        animationSpec = tween(100),
        label = "progress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 头部状态条
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (result.isTrackingActive) EmeraldSuccess else RoseError)
                    )
                    Text(
                        text = "${activeCategory.label} · 实时检测",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = result.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
            }

            // 若外部未显式限定分类，提供单选 Chip 切换，避免混在一起混乱
            if (category == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GestureCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = activeCategory == cat,
                            onClick = { activeCategory = cat },
                            label = { Text(cat.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            // 依据当前分类精确呈现对应视觉反馈
            when (activeCategory) {
                GestureCategory.HEAD -> {
                    // 头部姿态角度表盘 (仅展示俯仰、偏航、侧倾)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        PoseAngleBadge(label = "俯仰 Pitch", value = result.headPitch, suffix = "°")
                        PoseAngleBadge(label = "偏航 Yaw", value = result.headYaw, suffix = "°")
                        PoseAngleBadge(label = "侧倾 Roll", value = result.headRoll, suffix = "°")
                    }

                    if (config != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CalibrationRow(
                                title = "左右转头 (偏航)",
                                liveValueText = "%.1f°".format(result.headYaw),
                                thresholdText = "%.1f°".format(config.effectiveYawThreshold()),
                                ratio = (kotlin.math.abs(result.headYaw) / config.effectiveYawThreshold()).coerceIn(0f, 1f),
                                canCalibrate = kotlin.math.abs(result.headYaw) > 6f,
                                onCalibrate = { onCalibrateYaw?.invoke(result.headYaw) }
                            )

                            CalibrationRow(
                                title = "低头/抬头 (俯仰)",
                                liveValueText = "%.1f°".format(result.headPitch),
                                thresholdText = "%.1f°".format(config.effectivePitchThreshold()),
                                ratio = (kotlin.math.abs(result.headPitch) / config.effectivePitchThreshold()).coerceIn(0f, 1f),
                                canCalibrate = kotlin.math.abs(result.headPitch) > 5f,
                                onCalibrate = { onCalibratePitch?.invoke(result.headPitch) }
                            )
                        }
                    }
                }

                GestureCategory.GAZE -> {
                    // 四向全能眼动视线指示器 (展示视线上下左右 X/Y 偏离与双眼状态)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (result.isGazeTracked) EmeraldSuccess else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "四向视线眼动追踪",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = if (result.isGazeTracked) {
                                val hDir = when {
                                    result.gazeOffsetX > 0.15f -> "向右看"
                                    result.gazeOffsetX < -0.15f -> "向左看"
                                    else -> "水平居中"
                                }
                                val vDir = when {
                                    result.gazeOffsetY > 0.15f -> " · 向下看"
                                    result.gazeOffsetY < -0.15f -> " · 向上看"
                                    else -> " · 垂直居中"
                                }
                                "$hDir$vDir (X:%d%% Y:%d%%)".format(
                                    (result.gazeOffsetX * 100).toInt(),
                                    (result.gazeOffsetY * 100).toInt()
                                )
                            } else {
                                "双眼需保持睁开"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 2D 视线十字罗盘靶标与双眼开合指标组合
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Gaze2DCrosshair(
                            gazeX = result.gazeOffsetX,
                            gazeY = result.gazeOffsetY,
                            hThreshold = config?.effectiveGazeHThreshold() ?: 0.25f,
                            vThreshold = config?.effectiveGazeVThreshold() ?: 0.25f,
                            isTracked = result.isGazeTracked,
                            modifier = Modifier.weight(1f)
                        )

                        // 左右眼睁开指标
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FeatureIndicator(
                                label = "左眼睁开度",
                                value = (result.leftEyeOpenProb * 100).toInt(),
                                isWarning = result.leftEyeOpenProb < 0.35f
                            )
                            FeatureIndicator(
                                label = "右眼睁开度",
                                value = (result.rightEyeOpenProb * 100).toInt(),
                                isWarning = result.rightEyeOpenProb < 0.35f
                            )
                        }
                    }

                    if (config != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val liveXText = if (result.gazeOffsetX >= 0) "右看 +%d%%".format((result.gazeOffsetX * 100).toInt())
                                           else "左看 %d%%".format((result.gazeOffsetX * 100).toInt())
                            CalibrationRow(
                                title = "水平看边 (左/右看)",
                                liveValueText = liveXText,
                                thresholdText = "±%d%%".format((config.effectiveGazeHThreshold() * 100).toInt()),
                                ratio = (kotlin.math.abs(result.gazeOffsetX) / config.effectiveGazeHThreshold()).coerceIn(0f, 1f),
                                canCalibrate = kotlin.math.abs(result.gazeOffsetX) > 0.10f,
                                onCalibrate = { onCalibrateGaze?.invoke(result.gazeOffsetX) }
                            )

                            val liveYText = if (result.gazeOffsetY >= 0) "下看 +%d%%".format((result.gazeOffsetY * 100).toInt())
                                           else "上看 %d%%".format((result.gazeOffsetY * 100).toInt())
                            CalibrationRow(
                                title = "垂直看边 (上/下看)",
                                liveValueText = liveYText,
                                thresholdText = "±%d%%".format((config.effectiveGazeVThreshold() * 100).toInt()),
                                ratio = (kotlin.math.abs(result.gazeOffsetY) / config.effectiveGazeVThreshold()).coerceIn(0f, 1f),
                                canCalibrate = kotlin.math.abs(result.gazeOffsetY) > 0.10f,
                                onCalibrate = { onCalibrateGazeVertical?.invoke(result.gazeOffsetY) }
                            )
                        }
                    }
                }

                GestureCategory.FACIAL -> {
                    // 纯面部表情 (眨眼、张嘴、微笑)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FeatureIndicator(
                            label = "左眼睁开",
                            value = (result.leftEyeOpenProb * 100).toInt(),
                            isWarning = result.leftEyeOpenProb < 0.35f
                        )
                        FeatureIndicator(
                            label = "右眼睁开",
                            value = (result.rightEyeOpenProb * 100).toInt(),
                            isWarning = result.rightEyeOpenProb < 0.35f
                        )
                        FeatureIndicator(
                            label = "张嘴幅度",
                            value = (result.mouthOpenRatio * 100).toInt(),
                            isWarning = result.mouthOpenRatio > 0.28f
                        )
                        FeatureIndicator(
                            label = "微笑概率",
                            value = (result.smilingProb * 100).toInt(),
                            isWarning = result.smilingProb > 0.6f
                        )
                    }
                }

                GestureCategory.HAND -> {
                    // 纯手势追踪
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "当前手势捕获",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = result.handGesture?.displayName ?: "未识别到手势 (手掌入镜轻挥)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (result.handGesture != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 当前蓄力与触发反馈 (仅在当前分类有匹配候选时呈现)
            val candidate = result.candidateGesture
            val isCurrentCategoryCandidate = candidate != null && candidate.category == activeCategory
            if (isCurrentCategoryCandidate || (result.triggerHoldProgress > 0f && activeCategory == candidate?.category)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "蓄力触发: ${candidate?.displayName ?: ""}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                }
            } else if (result.lastFiredGesture != null && result.lastFiredGesture.category == activeCategory && (System.currentTimeMillis() - result.lastFiredTimestamp < 2000L)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EmeraldSuccess.copy(alpha = 0.15f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚡ 已触发: ${result.lastFiredGesture.displayName} ➔ ${result.lastFiredAction?.title}",
                        color = EmeraldSuccess,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CalibrationRow(
    title: String,
    liveValueText: String,
    thresholdText: String,
    ratio: Float,
    canCalibrate: Boolean,
    onCalibrate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "实测: $liveValueText",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canCalibrate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = "阈值: $thresholdText", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (ratio >= 1.0f) EmeraldSuccess else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
        }

        FilledTonalButton(
            onClick = onCalibrate,
            enabled = canCalibrate,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(26.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "采纳", fontSize = 10.sp)
        }
    }
}

@Composable
private fun PoseAngleBadge(label: String, value: Float, suffix: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = "%.1f%s".format(value, suffix),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (kotlin.math.abs(value) > 15f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FeatureIndicator(label: String, value: Int, isWarning: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "$value%",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isWarning) EmeraldSuccess else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Gaze2DCrosshair(
    gazeX: Float,
    gazeY: Float,
    hThreshold: Float,
    vThreshold: Float,
    isTracked: Boolean,
    modifier: Modifier = Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, outlineColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        // 4个方向微标签
        Text("上", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.TopCenter))
        Text("下", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.BottomCenter))
        Text("左", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterStart))
        Text("右", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterEnd))

        // 中心十字虚线
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(1.dp)
                .background(outlineColor.copy(alpha = 0.5f))
                .align(Alignment.Center)
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.7f)
                .background(outlineColor.copy(alpha = 0.5f))
                .align(Alignment.Center)
        )

        // 动态跟随光标点 (2D移动)
        val clampedX = gazeX.coerceIn(-1f, 1f)
        val clampedY = gazeY.coerceIn(-1f, 1f)
        val isExceeded = kotlin.math.abs(clampedX) >= hThreshold || kotlin.math.abs(clampedY) >= vThreshold

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(
                    x = (clampedX * 36).dp,
                    y = (clampedY * 26).dp
                )
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (!isTracked) Color.Gray
                    else if (isExceeded) EmeraldSuccess
                    else MaterialTheme.colorScheme.primary
                )
        )
    }
}
