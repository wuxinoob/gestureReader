package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.PostureZeroPointCard
import com.example.ui.components.PresetSelectorCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.userConfig.collectAsStateWithLifecycle()
    val recognitionResult by viewModel.recognitionResult.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 外观与主题模式切换卡片 (默认浅色，全面支持深色模式)
        item {
            Card(
                modifier = Modifier
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = if (config.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "界面主题与护眼色彩",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (config.isDarkMode) "深色墨竹护眼已开启 · 柔光低眩光 · OLED节电" else "竹青护眼浅色模式 (系统默认) · 纸本低对比度 · 减缓视疲劳",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = config.isDarkMode,
                            onCheckedChange = { viewModel.setDarkMode(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = !config.isDarkMode,
                            onClick = { viewModel.setDarkMode(false) },
                            modifier = Modifier.weight(1f),
                            label = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LightMode,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("浅色模式 (默认)")
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        FilterChip(
                            selected = config.isDarkMode,
                            onClick = { viewModel.setDarkMode(true) },
                            modifier = Modifier.weight(1f),
                            label = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DarkMode,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("深色模式")
                                }
                            },
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
        }

        // 场景预设一键应用
        item {
            PresetSelectorCard(
                currentConfig = config,
                onSelectPreset = { preset -> viewModel.applyPreset(preset) }
            )
        }

        // 姿态零点基准校准与微型罗盘
        item {
            PostureZeroPointCard(
                config = config,
                result = recognitionResult,
                onCalibrateZeroPoint = { viewModel.calibrateCurrentPostureAsZeroPoint() },
                onResetZeroPoint = { viewModel.resetPostureZeroPoint() },
                onCalibrateGazeZeroPoint = { viewModel.calibrateGazeZeroPoint() }
            )
        }

        // 头部姿态角度阈值组
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "头部姿态角度阈值 (自定义灵敏角度)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    ConfigSliderRow(
                        title = "仰头 / 低头触发角度 (Pitch)",
                        value = config.pitchThreshold,
                        formattedValue = "${config.pitchThreshold.toInt()}°",
                        range = 8f..35f,
                        steps = 27,
                        onValueChange = { viewModel.updateThresholds(pitch = it) }
                    )

                    ConfigSliderRow(
                        title = "向左 / 向右转头角度 (Yaw)",
                        value = config.yawThreshold,
                        formattedValue = "${config.yawThreshold.toInt()}°",
                        range = 10f..45f,
                        steps = 35,
                        onValueChange = { viewModel.updateThresholds(yaw = it) }
                    )

                    ConfigSliderRow(
                        title = "侧倾歪头触发角度 (Roll)",
                        value = config.rollThreshold,
                        formattedValue = "${config.rollThreshold.toInt()}°",
                        range = 10f..40f,
                        steps = 30,
                        onValueChange = { viewModel.updateThresholds(roll = it) }
                    )
                }
            }
        }

        // 面部动作幅度与表情阈值组
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "面部动作与表情触发阈值",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    ConfigSliderRow(
                        title = "闭眼 / 眨眼判定敏感度 (睁眼概率门限)",
                        value = config.eyeBlinkThreshold,
                        formattedValue = "${(config.eyeBlinkThreshold * 100).toInt()}%",
                        range = 0.15f..0.55f,
                        steps = 8,
                        onValueChange = { viewModel.updateThresholds(eyeBlink = it) }
                    )

                    ConfigSliderRow(
                        title = "张嘴动作幅度阈值 (嘴高宽比)",
                        value = config.mouthOpenThreshold,
                        formattedValue = "${(config.mouthOpenThreshold * 100).toInt()}%",
                        range = 0.15f..0.50f,
                        steps = 7,
                        onValueChange = { viewModel.updateThresholds(mouthOpen = it) }
                    )

                    ConfigSliderRow(
                        title = "微笑触发概率阈值",
                        value = config.smileThreshold,
                        formattedValue = "${(config.smileThreshold * 100).toInt()}%",
                        range = 0.35f..0.85f,
                        steps = 10,
                        onValueChange = { viewModel.updateThresholds(smile = it) }
                    )
                }
            }
        }

        // 眼动视线注视阈值组 (Gaze Tracking)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "眼动视线注视阈值 (Eye Gaze)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    ConfigSwitchRow(
                        title = "启用眼球视线追踪 (Gaze Tracking)",
                        desc = "通过前置镜头分析瞳孔虹膜偏转，用眼神注视控制翻页",
                        checked = config.gazeTrackingEnabled,
                        onCheckedChange = { viewModel.updateGazeSettings(enabled = it) }
                    )

                    if (config.gazeTrackingEnabled) {
                        ConfigSliderRow(
                            title = "横向视线触发偏转量 (水平眼动灵敏度)",
                            value = config.gazeHorizontalThreshold,
                            formattedValue = "${(config.gazeHorizontalThreshold * 100).toInt()}%",
                            range = 0.10f..0.40f,
                            steps = 15,
                            onValueChange = { viewModel.updateGazeSettings(hThreshold = it) }
                        )

                        ConfigSliderRow(
                            title = "纵向视线向下看触发偏转量 (读完页面下翻)",
                            value = config.gazeVerticalThreshold,
                            formattedValue = "${(config.gazeVerticalThreshold * 100).toInt()}%",
                            range = 0.12f..0.45f,
                            steps = 16,
                            onValueChange = { viewModel.updateGazeSettings(vThreshold = it) }
                        )

                        ConfigSliderRow(
                            title = "视线驻留时长 Dwell Time (防行间阅读误触)",
                            value = config.gazeDwellDurationMs.toFloat(),
                            formattedValue = "${config.gazeDwellDurationMs} ms",
                            range = 300f..1800f,
                            steps = 15,
                            onValueChange = { viewModel.updateGazeSettings(dwellMs = it.toLong()) }
                        )
                    }
                }
            }
        }

        // 时序与去抖动配置 (防误触)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "时序蓄力与冷却防误触 (Timing & Debounce)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    ConfigSliderRow(
                        title = "持续保持蓄力时长 (Hold Duration)",
                        value = config.holdDurationMs.toFloat(),
                        formattedValue = "${config.holdDurationMs} ms",
                        range = 120f..800f,
                        steps = 17,
                        onValueChange = { viewModel.updateTiming(holdMs = it.toLong()) }
                    )

                    ConfigSliderRow(
                        title = "触发后冷却时间 (防止连续误翻)",
                        value = config.cooldownMs.toFloat(),
                        formattedValue = "${config.cooldownMs} ms",
                        range = 400f..2000f,
                        steps = 16,
                        onValueChange = { viewModel.updateTiming(cooldownMs = it.toLong()) }
                    )
                }
            }
        }

        // 悬浮窗外观与反馈设置
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "悬浮窗视觉与触感反馈",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    ConfigSliderRow(
                        title = "悬浮窗整体不透明度 (Alpha)",
                        value = config.overlayAlpha,
                        formattedValue = "${(config.overlayAlpha * 100).toInt()}%",
                        range = 0.40f..1.0f,
                        steps = 12,
                        onValueChange = { viewModel.updateOverlayAppearance(alpha = it) }
                    )

                    ConfigSwitchRow(
                        title = "绘制前置摄像头取景小窗",
                        desc = "日常阅读默认关闭以保持纯净沉浸、零画面遮挡；仅需调试或校准动作时开启",
                        checked = config.showCameraPreview,
                        onCheckedChange = { viewModel.updateOverlayAppearance(showPreview = it) }
                    )

                    ConfigSwitchRow(
                        title = "震动反馈 (触感)",
                        desc = "每次手势或动作成功触发时产生轻微触感振动",
                        checked = config.hapticFeedback,
                        onCheckedChange = { viewModel.updateFeedback(haptic = it) }
                    )

                    ConfigSwitchRow(
                        title = "提示音反馈",
                        desc = "触发时发出短促哔声确认",
                        checked = config.soundFeedback,
                        onCheckedChange = { viewModel.updateFeedback(sound = it) }
                    )
                }
            }
        }

        // 功耗优化与后台保活设置
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "功耗优化与后台保活设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // 帧率采样控制
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "动作采样率 (隔帧检测)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = when (config.frameSkipRate) {
                                    1 -> "30 FPS · 全速模式"
                                    2 -> "15 FPS · 均衡省电 (推荐)"
                                    else -> "10 FPS · 极致省电"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                3 to "10 FPS 极省电",
                                2 to "15 FPS 推荐",
                                1 to "30 FPS 全速"
                            ).forEach { (rate, label) ->
                                FilterChip(
                                    selected = config.frameSkipRate == rate,
                                    onClick = { viewModel.updatePerformanceOptions(frameSkipRate = rate) },
                                    label = { Text(label, fontSize = 11.sp) },
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

                    // 算法分辨率
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "图像分析分辨率", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = when (config.resolutionTier) {
                                    "LOW" -> "480x360 (轻量省电 · 发热最小)"
                                    "HIGH" -> "1280x720 (超清高精 · 功耗高)"
                                    else -> "640x480 (标准均衡)"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "LOW" to "轻量 480p",
                                "MEDIUM" to "标准 640p"
                            ).forEach { (tier, label) ->
                                FilterChip(
                                    selected = config.resolutionTier == tier,
                                    onClick = { viewModel.updatePerformanceOptions(resolutionTier = tier) },
                                    label = { Text(label, fontSize = 11.sp) },
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

                    // 智能节能优化开关
                    ConfigSwitchRow(
                        title = "智能离镜休眠降频 (Auto Idle Standby)",
                        desc = "离镜或无人脸3秒后自动将分析频率降至 2.5 FPS 哨兵模式，芯片与功耗骤降 70%，人脸入镜瞬时拉起",
                        checked = config.autoIdleSleep,
                        onCheckedChange = { viewModel.updateEcoOptions(autoIdleSleep = it) }
                    )

                    ConfigSwitchRow(
                        title = "息屏释放硬件 (Screen-off Power Cut)",
                        desc = "手机电源键锁屏或屏幕熄灭时，立即注销前置摄像头硬件并暂停分析，待机耗电 0%",
                        checked = config.screenOffPause,
                        onCheckedChange = { viewModel.updateEcoOptions(screenOffPause = it) }
                    )

                    // 电池白名单 (防杀无障碍服务)
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                    } else true

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "忽略电池优化 (防杀无障碍服务)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isIgnoring) "已加入电池优化白名单，后台稳定防杀" else "关闭应用后无障碍被重置通常为系统省电策略导致，建议加入白名单",
                                fontSize = 10.sp,
                                color = if (isIgnoring) EmeraldSuccess else AmberWarning
                            )
                        }
                        if (!isIgnoring && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(fallback)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("立即授权", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // 恢复默认按钮
        item {
            FilledTonalButton(
                onClick = { viewModel.resetToDefaults() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "恢复全部默认阈值与设置", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ConfigSliderRow(
    title: String,
    value: Float,
    formattedValue: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(text = formattedValue, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun ConfigSwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
