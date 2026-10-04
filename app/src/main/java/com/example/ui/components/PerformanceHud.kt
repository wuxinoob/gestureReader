package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PerformanceMetrics
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError

@Composable
fun PerformanceHud(
    metrics: PerformanceMetrics,
    modifier: Modifier = Modifier
) {
    val latencyColor = when {
        metrics.inferenceTimeMs == 0L -> Color.Gray
        metrics.inferenceTimeMs < 25L -> EmeraldSuccess
        metrics.inferenceTimeMs < 50L -> AmberWarning
        else -> RoseError
    }

    val fpsColor = when {
        metrics.fps >= 24f -> EmeraldSuccess
        metrics.fps >= 15f -> AmberWarning
        else -> Color.Gray
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EmeraldSuccess)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "端侧本地处理指标 (On-Device HUD)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "完全离线运行",
                    fontSize = 11.sp,
                    color = EmeraldSuccess,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricPill(
                    modifier = Modifier.weight(1f),
                    title = "实时帧率 FPS",
                    value = if (metrics.fps > 0) "%.1f".format(metrics.fps) else "--",
                    unit = "fps",
                    statusColor = fpsColor
                )

                MetricPill(
                    modifier = Modifier.weight(1f),
                    title = "推理耗时 Latency",
                    value = if (metrics.inferenceTimeMs > 0) "${metrics.inferenceTimeMs}" else "--",
                    unit = "ms",
                    statusColor = latencyColor
                )

                MetricPill(
                    modifier = Modifier.weight(1f),
                    title = "丢帧统计 Drops",
                    value = "${metrics.droppedFramesCount}",
                    unit = "frames",
                    statusColor = if (metrics.droppedFramesCount < 10) EmeraldSuccess else AmberWarning
                )
            }

            // 负载评估条
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "计算负载评估", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${(metrics.cpuLoadEstimate * 100).toInt()}% (帧内预算占用)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = latencyColor
                    )
                }
                LinearProgressIndicator(
                    progress = { metrics.cpuLoadEstimate },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = latencyColor,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            }

            // 识别器在线状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusTag(
                    label = "头部/面部模型",
                    isActive = metrics.isFaceTracked,
                    detail = if (metrics.isFaceTracked) "已锁定目标" else "等待入镜"
                )
                StatusTag(
                    label = "手势分析器",
                    isActive = metrics.isHandTracked,
                    detail = if (metrics.isHandTracked) "手部捕捉中" else "未出现手部"
                )
            }
        }
    }
}

@Composable
private fun MetricPill(
    title: String,
    value: String,
    unit: String,
    statusColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp)
    ) {
        Text(text = title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(text = unit, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun StatusTag(label: String, isActive: Boolean, detail: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isActive) EmeraldSuccess else Color.Gray)
        )
        Text(
            text = "$label: $detail",
            fontSize = 11.sp,
            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
