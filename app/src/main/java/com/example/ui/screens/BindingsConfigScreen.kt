package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ActionType
import com.example.model.GestureCategory
import com.example.model.RecognitionResult
import com.example.model.TriggerGesture
import com.example.model.UserConfig
import com.example.ui.components.GestureVisualizer
import com.example.ui.components.PostureZeroPointCard
import com.example.ui.components.PresetSelectorCard
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError

import com.example.ui.viewmodel.MainViewModel
import kotlin.math.abs

@Composable
fun BindingsConfigScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bindings by viewModel.bindings.collectAsStateWithLifecycle()
    val config by viewModel.userConfig.collectAsStateWithLifecycle()
    val recognitionResult by viewModel.recognitionResult.collectAsStateWithLifecycle()
    val isOverlayRunning by viewModel.isOverlayRunning.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf(GestureCategory.HEAD) }
    var selectedGestureForBindingDialog by remember { mutableStateOf<TriggerGesture?>(null) }

    val currentCategoryGestures = remember(selectedCategory) {
        TriggerGesture.entries.filter { it.category == selectedCategory }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 应用内即时调试模式状态条 (无需使用悬浮窗也能在当前页直接测试)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isOverlayRunning) EmeraldSuccess else Color.Gray)
                            )
                            Column {
                                Text(
                                    text = if (isOverlayRunning) "应用内调试运行中" else "应用内调试未启动",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isOverlayRunning) "做出动作即可在下方实时观察与采纳阈值" else "无需依赖悬浮窗，点击即可直接测试",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                if (!isOverlayRunning) {
                                    viewModel.startOverlayService(context)
                                    Toast.makeText(context, "🟢 识别服务已启动，可在当前页面直接测试动作", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.toggleTrackingPause()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isOverlayRunning) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                contentColor = if (isOverlayRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = if (!isOverlayRunning) "启动调试" else if (config.isPaused) "恢复" else "暂停",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 核心快捷方法：点击进入阈值调试状态时关闭动作触发，方便调试
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (config.debugModeNoTrigger) EmeraldSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                val next = !config.debugModeNoTrigger
                                viewModel.setDebugModeNoTrigger(next)
                                if (next) {
                                    Toast.makeText(context, "🎯 调试模式已开启：动作触发已静默，可安心测试角度与视线", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "⚡ 真实动作触发已恢复", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = if (config.debugModeNoTrigger) "🛡️" else "⚙️", fontSize = 13.sp)
                            Column {
                                Text(
                                    text = if (config.debugModeNoTrigger) "阈值调试模式 · 动作触发已关闭" else "真实执行模式 · 动作将触发翻页",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (config.debugModeNoTrigger) EmeraldSuccess else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (config.debugModeNoTrigger) "此时随意转头与注视仅刷新量表，不会模拟翻页跳屏" else "点击开启调试免扰，随意测试不会误触翻页",
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = config.debugModeNoTrigger,
                            onCheckedChange = { next ->
                                viewModel.setDebugModeNoTrigger(next)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EmeraldSuccess,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }

        // 场景化配置预设快速切换 (一键切换卧姿/支架/防抖/极速)
        item {
            PresetSelectorCard(
                currentConfig = config,
                onSelectPreset = { preset -> viewModel.applyPreset(preset) }
            )
        }

        // 姿态零点基准校准与微型罗盘 (相对零点归零)
        item {
            PostureZeroPointCard(
                config = config,
                result = recognitionResult,
                onCalibrateZeroPoint = { viewModel.calibrateCurrentPostureAsZeroPoint() },
                onResetZeroPoint = { viewModel.resetPostureZeroPoint() },
                onCalibrateGazeZeroPoint = { viewModel.calibrateGazeZeroPoint() }
            )
        }

        // 2. 子分类专属选择标签栏
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GestureCategory.entries.forEach { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = "${category.label} (${TriggerGesture.entries.count { it.category == category }})",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }

        // 3. 关键特性：子分类专属的识别检测与视觉反馈面板 (完全隔离无关类别，视觉聚焦)
        item {
            GestureVisualizer(
                result = recognitionResult,
                config = config,
                category = selectedCategory,
                onCalibratePitch = { p ->
                    viewModel.calibratePitchThreshold(p)
                    Toast.makeText(context, "✅ 仰俯阈值已优化更新", Toast.LENGTH_SHORT).show()
                },
                onCalibrateYaw = { y ->
                    viewModel.calibrateYawThreshold(y)
                    Toast.makeText(context, "✅ 偏航阈值已优化更新", Toast.LENGTH_SHORT).show()
                },
                onCalibrateGaze = { g ->
                    viewModel.calibrateGazeThreshold(g)
                    Toast.makeText(context, "✅ 水平视线阈值已优化更新", Toast.LENGTH_SHORT).show()
                },
                onCalibrateGazeVertical = { v ->
                    viewModel.calibrateGazeVerticalThreshold(v)
                    Toast.makeText(context, "✅ 垂直视线阈值已优化更新", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 4. 融合动作映射与专属阈值微调项列表 (专卡专调，零多余无关干扰)
        items(currentCategoryGestures, key = { it.id }) { gesture ->
            val entity = bindings.find { it.gestureId == gesture.id }
            val isEnabled = entity?.isEnabled ?: true
            val boundAction = entity?.toAction() ?: ActionType.NONE

            FusedBindingThresholdCard(
                gesture = gesture,
                boundAction = boundAction,
                isEnabled = isEnabled,
                config = config,
                result = recognitionResult,
                onToggleEnabled = { viewModel.toggleBinding(gesture, it) },
                onSelectActionClick = { selectedGestureForBindingDialog = gesture },
                onTestSimulate = { viewModel.testSimulateAction(boundAction) },
                onUpdateThresholds = { p, y, r, b, m, s ->
                    viewModel.updateThresholds(p, y, r, b, m, s)
                },
                onUpdateGaze = { h, v, d ->
                    viewModel.updateGazeSettings(hThreshold = h, vThreshold = v, dwellMs = d)
                },
                onCalibrateYaw = {
                    viewModel.calibrateYawThreshold(recognitionResult.headYaw)
                    Toast.makeText(context, "✅ 偏航阈值已按当前画面设置", Toast.LENGTH_SHORT).show()
                },
                onCalibratePitch = {
                    viewModel.calibratePitchThreshold(recognitionResult.headPitch)
                    Toast.makeText(context, "✅ 仰俯阈值已按当前画面设置", Toast.LENGTH_SHORT).show()
                },
                onCalibrateGaze = {
                    viewModel.calibrateGazeThreshold(recognitionResult.gazeOffsetX)
                    Toast.makeText(context, "✅ 水平视线阈值已按当前画面设置", Toast.LENGTH_SHORT).show()
                },
                onCalibrateGazeVertical = {
                    viewModel.calibrateGazeVerticalThreshold(recognitionResult.gazeOffsetY)
                    Toast.makeText(context, "✅ 垂直视线阈值已按当前画面设置", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 底部重置按钮
        item {
            FilledTonalButton(
                onClick = {
                    viewModel.resetToDefaults()
                    Toast.makeText(context, "已恢复全部动作与阈值为默认值", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "恢复默认所有动作绑定与阈值设置", fontSize = 12.sp)
            }
        }
    }

    // 动作选择弹窗
    selectedGestureForBindingDialog?.let { gesture ->
        val currentEntity = bindings.find { it.gestureId == gesture.id }
        val currentAction = currentEntity?.toAction() ?: ActionType.NONE

        ActionSelectionDialog(
            gesture = gesture,
            currentAction = currentAction,
            onActionSelected = { action ->
                viewModel.updateBindingAction(gesture, action)
                selectedGestureForBindingDialog = null
            },
            onDismiss = { selectedGestureForBindingDialog = null }
        )
    }
}

/**
 * 融合动作映射与专属触发阈值卡片：
 * 绝不在配置某个阈值时涌入过多无关信息，仅聚焦呈现当前动作的映射动作与对应阈值条
 */
@Composable
private fun FusedBindingThresholdCard(
    gesture: TriggerGesture,
    boundAction: ActionType,
    isEnabled: Boolean,
    config: UserConfig,
    result: RecognitionResult,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectActionClick: () -> Unit,
    onTestSimulate: () -> Unit,
    onUpdateThresholds: (Float?, Float?, Float?, Float?, Float?, Float?) -> Unit,
    onUpdateGaze: (Float?, Float?, Long?) -> Unit,
    onCalibrateYaw: () -> Unit,
    onCalibratePitch: () -> Unit,
    onCalibrateGaze: () -> Unit,
    onCalibrateGazeVertical: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 头部：手势名称与启用开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = gesture.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = gesture.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            }

            // 动作映射选择框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = isEnabled, onClick = onSelectActionClick)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "映射操作: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = boundAction.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isEnabled && boundAction != ActionType.NONE) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable(onClick = onTestSimulate)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(text = "模拟触发", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 核心融合：仅展示当前动作对应的特定阈值设置与实时达标反馈条 (完全排除无关噪音)
            if (isEnabled) {
                when (gesture) {
                    TriggerGesture.HEAD_YAW_LEFT, TriggerGesture.HEAD_YAW_RIGHT -> {
                        val liveYaw = abs(result.headYaw)
                        val effThreshold = config.effectiveYawThreshold()
                        val progress = (liveYaw / effThreshold).coerceIn(0f, 1f)

                        InlineThresholdTuningSection(
                            title = "偏航触发阈值 (左右转头)",
                            currentValue = config.yawThreshold,
                            formattedValue = "${config.yawThreshold.toInt()}°",
                            liveText = "实测偏航: ${liveYaw.toInt()}°",
                            progress = progress,
                            valueRange = 8f..35f,
                            steps = 27,
                            canCalibrate = liveYaw > 6f,
                            onValueChange = { onUpdateThresholds(null, it, null, null, null, null) },
                            onCalibrateClick = onCalibrateYaw
                        )
                    }

                    TriggerGesture.HEAD_PITCH_UP, TriggerGesture.HEAD_PITCH_DOWN -> {
                        val livePitch = abs(result.headPitch)
                        val effThreshold = config.effectivePitchThreshold()
                        val progress = (livePitch / effThreshold).coerceIn(0f, 1f)

                        InlineThresholdTuningSection(
                            title = "俯仰触发阈值 (抬头/低头)",
                            currentValue = config.pitchThreshold,
                            formattedValue = "${config.pitchThreshold.toInt()}°",
                            liveText = "实测俯仰: ${livePitch.toInt()}°",
                            progress = progress,
                            valueRange = 6f..30f,
                            steps = 24,
                            canCalibrate = livePitch > 5f,
                            onValueChange = { onUpdateThresholds(it, null, null, null, null, null) },
                            onCalibrateClick = onCalibratePitch
                        )
                    }

                    TriggerGesture.HEAD_ROLL_LEFT, TriggerGesture.HEAD_ROLL_RIGHT -> {
                        val liveRoll = abs(result.headRoll)
                        InlineThresholdTuningSection(
                            title = "侧倾触发阈值 (歪头)",
                            currentValue = config.rollThreshold,
                            formattedValue = "${config.rollThreshold.toInt()}°",
                            liveText = "实测侧倾: ${liveRoll.toInt()}°",
                            progress = (liveRoll / config.rollThreshold).coerceIn(0f, 1f),
                            valueRange = 8f..35f,
                            steps = 27,
                            canCalibrate = false,
                            onValueChange = { onUpdateThresholds(null, null, it, null, null, null) },
                            onCalibrateClick = {}
                        )
                    }

                    TriggerGesture.GAZE_LOOK_RIGHT, TriggerGesture.GAZE_LOOK_LEFT -> {
                        val liveGazeX = abs(result.gazeOffsetX)
                        val effThreshold = config.effectiveGazeHThreshold()
                        val progress = (liveGazeX / effThreshold).coerceIn(0f, 1f)

                        InlineThresholdTuningSection(
                            title = "视线横向偏转阈值 (看左/看右)",
                            currentValue = config.gazeHorizontalThreshold,
                            formattedValue = "${(config.gazeHorizontalThreshold * 100).toInt()}%",
                            liveText = "实测视线: ${(liveGazeX * 100).toInt()}%",
                            progress = progress,
                            valueRange = 0.12f..0.45f,
                            steps = 33,
                            canCalibrate = liveGazeX > 0.10f,
                            onValueChange = { onUpdateGaze(it, null, null) },
                            onCalibrateClick = onCalibrateGaze
                        )
                    }

                    TriggerGesture.GAZE_LOOK_UP -> {
                        val liveGazeY = result.gazeOffsetY
                        val effThreshold = config.effectiveGazeVThreshold()
                        val progress = ((-liveGazeY) / effThreshold).coerceIn(0f, 1f)

                        InlineThresholdTuningSection(
                            title = "向上注视深度阈值 (看顶部翻回)",
                            currentValue = config.gazeVerticalThreshold,
                            formattedValue = "${(config.gazeVerticalThreshold * 100).toInt()}%",
                            liveText = "实测向上: ${((-liveGazeY) * 100).coerceAtLeast(0f).toInt()}%",
                            progress = progress,
                            valueRange = 0.12f..0.50f,
                            steps = 38,
                            canCalibrate = liveGazeY < -0.10f,
                            onValueChange = { onUpdateGaze(null, it, null) },
                            onCalibrateClick = onCalibrateGazeVertical
                        )
                    }

                    TriggerGesture.GAZE_LOOK_DOWN -> {
                        val liveGazeY = result.gazeOffsetY
                        val effThreshold = config.effectiveGazeVThreshold()
                        val progress = (liveGazeY / effThreshold).coerceIn(0f, 1f)

                        InlineThresholdTuningSection(
                            title = "向下注视深度阈值 (读至底端下翻)",
                            currentValue = config.gazeVerticalThreshold,
                            formattedValue = "${(config.gazeVerticalThreshold * 100).toInt()}%",
                            liveText = "实测向下: ${(liveGazeY * 100).coerceAtLeast(0f).toInt()}%",
                            progress = progress,
                            valueRange = 0.12f..0.50f,
                            steps = 38,
                            canCalibrate = liveGazeY > 0.10f,
                            onValueChange = { onUpdateGaze(null, it, null) },
                            onCalibrateClick = onCalibrateGazeVertical
                        )
                    }

                    TriggerGesture.GAZE_DWELL_CORNER -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "角落驻留确认时长", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${config.gazeDwellDurationMs} ms",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = config.gazeDwellDurationMs.toFloat(),
                                onValueChange = { onUpdateGaze(null, null, it.toLong()) },
                                valueRange = 250f..1000f,
                                steps = 15,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }

                    TriggerGesture.BLINK_LEFT, TriggerGesture.BLINK_RIGHT, TriggerGesture.BLINK_BOTH -> {
                        val liveEye = if (gesture == TriggerGesture.BLINK_RIGHT) result.rightEyeOpenProb else result.leftEyeOpenProb
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "闭眼判定阈值 (睁开概率低于此值触发)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${(config.eyeBlinkThreshold * 100).toInt()}% (实测: ${(liveEye * 100).toInt()}%)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (liveEye < config.eyeBlinkThreshold) EmeraldSuccess else MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = config.eyeBlinkThreshold,
                                onValueChange = { onUpdateThresholds(null, null, null, it, null, null) },
                                valueRange = 0.15f..0.45f,
                                steps = 30,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }

                    TriggerGesture.MOUTH_OPEN -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "张嘴幅度阈值", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${(config.mouthOpenThreshold * 100).toInt()}% (实测: ${(result.mouthOpenRatio * 100).toInt()}%)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = config.mouthOpenThreshold,
                                onValueChange = { onUpdateThresholds(null, null, null, null, it, null) },
                                valueRange = 0.15f..0.50f,
                                steps = 35,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }

                    TriggerGesture.SMILE -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "微笑概率阈值", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${(config.smileThreshold * 100).toInt()}% (实测: ${(result.smilingProb * 100).toInt()}%)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = config.smileThreshold,
                                onValueChange = { onUpdateThresholds(null, null, null, null, null, it) },
                                valueRange = 0.40f..0.90f,
                                steps = 25,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }

                    else -> {
                        // 手部动作或无需单独角度阈值的动作
                    }
                }
            }
        }
    }
}

/**
 * 单动作阈值微调组件：含当前阈值滑块、实时识别读数、触表达标进度条与一键采纳按键
 */
@Composable
private fun InlineThresholdTuningSection(
    title: String,
    currentValue: Float,
    formattedValue: String,
    liveText: String,
    progress: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    canCalibrate: Boolean,
    onValueChange: (Float) -> Unit,
    onCalibrateClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = liveText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (progress >= 1f) EmeraldSuccess else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "阈值: $formattedValue",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 实时达标进度指示条
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (progress >= 1.0f) EmeraldSuccess else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Slider(
                value = currentValue,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            if (canCalibrate) {
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onCalibrateClick,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(text = "采纳当前", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ActionSelectionDialog(
    gesture: TriggerGesture,
    currentAction: ActionType,
    onActionSelected: (ActionType) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGroup by remember { mutableStateOf("ALL") }

    val filteredActions = remember(selectedGroup) {
        when (selectedGroup) {
            "PAGE" -> listOf(ActionType.SWIPE_LEFT, ActionType.SWIPE_RIGHT, ActionType.SWIPE_UP, ActionType.SWIPE_DOWN)
            "SCROLL" -> listOf(ActionType.SCROLL_DOWN_SLOW, ActionType.SCROLL_UP_SLOW)
            "SYSTEM" -> listOf(ActionType.CLICK_CENTER, ActionType.BACK_KEY, ActionType.HOME_KEY)
            "NONE" -> listOf(ActionType.NONE)
            else -> ActionType.entries
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "选择映射动作", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "检测到「${gesture.displayName}」时执行：", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                // 快捷语义分类选择器
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val groups = listOf(
                        "ALL" to "全部",
                        "PAGE" to "📖 翻页",
                        "SCROLL" to "📜 滚动",
                        "SYSTEM" to "📱 系统"
                    )
                    groups.forEach { (groupId, label) ->
                        val isSelected = selectedGroup == groupId
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedGroup = groupId },
                            label = { Text(text = label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredActions) { action ->
                    val isCurrent = action == currentAction
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onActionSelected(action) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = action.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = action.description, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
