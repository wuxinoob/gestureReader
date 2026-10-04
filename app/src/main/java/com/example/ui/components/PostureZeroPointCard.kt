package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RecognitionResult
import com.example.model.UserConfig
import com.example.ui.theme.EmeraldSuccess
import kotlin.math.abs

@Composable
fun PostureZeroPointCard(
    config: UserConfig,
    result: RecognitionResult,
    onCalibrateZeroPoint: () -> Unit,
    onResetZeroPoint: () -> Unit,
    modifier: Modifier = Modifier,
    onCalibrateGazeZeroPoint: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val hasPostureBaseline = abs(config.baselinePitch) > 0.5f || abs(config.baselineYaw) > 0.5f || abs(config.baselineRoll) > 0.5f
    val hasGazeBaseline = abs(config.baselineGazeX) > 0.02f || abs(config.baselineGazeY) > 0.02f
    val hasBaseline = hasPostureBaseline || hasGazeBaseline

    // 动画平滑姿态点
    val normYaw = (result.headYaw / 30f).coerceIn(-1f, 1f)
    val normPitch = (-result.headPitch / 30f).coerceIn(-1f, 1f) // 俯仰逆向映射到坐标Y

    val animatedDotX by animateFloatAsState(targetValue = normYaw, animationSpec = tween(100), label = "dot_x")
    val animatedDotY by animateFloatAsState(targetValue = normPitch, animationSpec = tween(100), label = "dot_y")

    // 视线十字点
    val animatedGazeX by animateFloatAsState(targetValue = result.gazeOffsetX.coerceIn(-1f, 1f), animationSpec = tween(100), label = "gaze_x")
    val animatedGazeY by animateFloatAsState(targetValue = result.gazeOffsetY.coerceIn(-1f, 1f), animationSpec = tween(100), label = "gaze_y")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "🎯", fontSize = 16.sp)
                    Column {
                        Text(
                            text = "舒适阅读相对零点基准 (姿态与视线)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (hasBaseline) "已应用个性化偏置 · 动作以当前舒适姿态与视线为 (0,0)" else "当前以物理水平为零点 · 建议在舒适坐姿下点击校准",
                            fontSize = 10.sp,
                            color = if (hasBaseline) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (hasBaseline) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(EmeraldSuccess.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = "已相对归零", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                    }
                }
            }

            // 中间交互区：左侧迷你十字罗盘，右侧实时相对读数与偏置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 迷你罗盘 (76dp x 76dp)
                val outlineColor = MaterialTheme.colorScheme.outline
                val primaryColor = MaterialTheme.colorScheme.primary

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, outlineColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(76.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = cx * 0.85f

                        // 十字瞄准线
                        drawLine(
                            color = outlineColor.copy(alpha = 0.6f),
                            start = Offset(cx - r, cy),
                            end = Offset(cx + r, cy),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = outlineColor.copy(alpha = 0.6f),
                            start = Offset(cx, cy - r),
                            end = Offset(cx, cy + r),
                            strokeWidth = 1.dp.toPx()
                        )

                        // 舒适内圈 (死区)
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.25f),
                            radius = r * 0.4f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )

                        // 实时头部姿态绿点
                        val dotCenter = Offset(
                            x = cx + animatedDotX * (r * 0.8f),
                            y = cy + animatedDotY * (r * 0.8f)
                        )
                        drawCircle(
                            color = EmeraldSuccess,
                            radius = 4.dp.toPx(),
                            center = dotCenter
                        )

                        // 实时视线注视点 (空心青蓝环)
                        if (result.isGazeTracked) {
                            val gazeCenter = Offset(
                                x = cx + animatedGazeX * (r * 0.8f),
                                y = cy + animatedGazeY * (r * 0.8f)
                            )
                            drawCircle(
                                color = primaryColor,
                                radius = 5.dp.toPx(),
                                center = gazeCenter,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }

                // 右侧读数展示
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val p = result.headPitch.toInt()
                    val y = result.headYaw.toInt()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "相对头部 (Pitch/Yaw):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${if (p > 0) "+$p" else "$p"}° / ${if (y > 0) "+$y" else "$y"}°",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (abs(p) > config.pitchThreshold || abs(y) > config.yawThreshold) EmeraldSuccess else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "相对视线 (Gaze X/Y):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (result.isGazeTracked) {
                                "${if (result.gazeOffsetX > 0) "+" else ""}${String.format("%.2f", result.gazeOffsetX)}, ${if (result.gazeOffsetY > 0) "+" else ""}${String.format("%.2f", result.gazeOffsetY)}"
                            } else "未追踪",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (result.isGazeTracked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (hasBaseline) {
                        Text(
                            text = "零点偏置: 姿态(${config.baselinePitch.toInt()}°, ${config.baselineYaw.toInt()}°) · 视线(${String.format("%.2f", config.baselineGazeX)}, ${String.format("%.2f", config.baselineGazeY)})",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "平视屏幕中心，点下方按钮将此姿态与视线设为 (0,0)",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 操作按钮组
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onCalibrateZeroPoint()
                        Toast.makeText(context, "🎯 已将当前姿态与视线标定为中心零点 (0, 0)", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "一键全归零", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (onCalibrateGazeZeroPoint != null && result.isGazeTracked) {
                    OutlinedButton(
                        onClick = {
                            onCalibrateGazeZeroPoint()
                            Toast.makeText(context, "👁️ 视线中心已独立归零", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(text = "校准视线", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        onResetZeroPoint()
                        Toast.makeText(context, "↺ 姿态与视线已重置为标准物理零点", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(0.9f)
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(text = "重置", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
