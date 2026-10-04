package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ActionType
import com.example.ui.components.GestureVisualizer
import com.example.ui.components.PerformanceHud
import com.example.ui.components.PostureZeroPointCard
import com.example.ui.components.PresetSelectorCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isOverlayRunning by viewModel.isOverlayRunning.collectAsStateWithLifecycle()
    val isAccessibilityBound by viewModel.isAccessibilityBound.collectAsStateWithLifecycle()
    val hasOverlayPerm by viewModel.systemAlertPermission.collectAsStateWithLifecycle()
    val hasAccPerm by viewModel.accessibilityPermission.collectAsStateWithLifecycle()
    val recognitionResult by viewModel.recognitionResult.collectAsStateWithLifecycle()
    val performanceMetrics by viewModel.performanceMetrics.collectAsStateWithLifecycle()
    val config by viewModel.userConfig.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        viewModel.refreshPermissions()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                }
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部控制大卡片 (Hero Status & Toggle)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "无障碍悬浮追踪服务",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isOverlayRunning) "前置摄像头追踪中 · 悬浮窗已开启" else "服务未启动 · 点击开启并在任意阅读应用中使用",
                                fontSize = 12.sp,
                                color = if (isOverlayRunning) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isOverlayRunning) EmeraldSuccess else Color.Gray)
                        )
                    }

                    // 主开关按钮
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            if (!hasCameraPermission) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                return@Button
                            }
                            if (!hasOverlayPerm) {
                                MainViewModel.openOverlaySettings(context)
                                return@Button
                            }
                            if (isOverlayRunning) {
                                viewModel.stopOverlayService(context)
                            } else {
                                viewModel.startOverlayService(context)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOverlayRunning) RoseError else MaterialTheme.colorScheme.primary,
                            contentColor = if (isOverlayRunning) Color.White else MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isOverlayRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOverlayRunning) "停止悬浮追踪服务" else "开启无障碍手势阅读",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isOverlayRunning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.toggleTrackingPause() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (config.isPaused) "恢复识别" else "挂起暂停")
                            }

                            OutlinedButton(
                                onClick = { viewModel.refreshPermissions() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text("刷新状态")
                            }
                        }
                    }

                    // 快捷方法：阈值调试静默模式 (关闭动作触发，方便测试不跳屏)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (config.debugModeNoTrigger) EmeraldSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewModel.setDebugModeNoTrigger(!config.debugModeNoTrigger) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = if (config.debugModeNoTrigger) "🛡️" else "⚙️", fontSize = 14.sp)
                            Column {
                                Text(
                                    text = if (config.debugModeNoTrigger) "阈值调试模式 (动作静默已激活)" else "真实翻页模式 (动作触发模拟)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (config.debugModeNoTrigger) EmeraldSuccess else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (config.debugModeNoTrigger) "随意转头/眼动仅刷新量表，不触发返回或翻页动作" else "开启调试模式关闭动作触发，方便调参不跳屏",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = config.debugModeNoTrigger,
                            onCheckedChange = { viewModel.setDebugModeNoTrigger(it) },
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

        // 权限检查卡片 (若有权限未授予，置顶提示)
        val isAllGranted = hasCameraPermission && hasOverlayPerm && (hasAccPerm || isAccessibilityBound) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasNotificationPermission)

        if (!isAllGranted) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "需要配置以下系统权限以完整生效",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberWarning
                            )
                        }

                        // 相机权限
                        PermissionRow(
                            title = "前置摄像头权限",
                            desc = "本地用于捕捉头部动作与手势识别",
                            isGranted = hasCameraPermission,
                            onGrant = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                        )

                        // 悬浮窗权限
                        PermissionRow(
                            title = "显示在其他应用上 (悬浮窗)",
                            desc = "在微信读书/各类电子书阅读器上方展示控制器",
                            isGranted = hasOverlayPerm,
                            onGrant = { MainViewModel.openOverlaySettings(context) }
                        )

                        // 无障碍服务
                        PermissionRow(
                            title = "无障碍翻页服务",
                            desc = "模拟向上/下/左/右滑动与点击手势",
                            isGranted = hasAccPerm || isAccessibilityBound,
                            onGrant = { MainViewModel.openAccessibilitySettings(context) }
                        )

                        // Android 13+ 通知栏常驻权限
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            PermissionRow(
                                title = "通知栏常驻权限",
                                desc = "在系统通知中心保持常驻并提供快捷暂停/停止控制",
                                isGranted = hasNotificationPermission,
                                onGrant = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            )
                        }
                    }
                }
            }
        }

        // 场景化阅读配置预设一键切换
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

        // 实时姿态与手势监控组件 (含实时识别值比对与一键智能阈值优化)
        item {
            GestureVisualizer(
                result = recognitionResult,
                config = config,
                onCalibratePitch = { p -> viewModel.calibratePitchThreshold(p) },
                onCalibrateYaw = { y -> viewModel.calibrateYawThreshold(y) },
                onCalibrateGaze = { g -> viewModel.calibrateGazeThreshold(g) }
            )
        }

        // 实时性能 HUD 指标
        item {
            PerformanceHud(metrics = performanceMetrics)
        }

        // 快速模拟测试工具箱
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "无障碍动作即时测试",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isAccessibilityBound) "服务已连接" else "需先开启无障碍",
                            fontSize = 11.sp,
                            color = if (isAccessibilityBound) EmeraldSuccess else RoseError
                        )
                    }

                    Text(
                        text = "点击下方按钮可直接测试模拟手势执行，检查阅读器翻页效果：",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionButton(
                            text = "下翻一页 ⬇",
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.testSimulateAction(ActionType.SWIPE_UP)
                        }

                        QuickActionButton(
                            text = "上翻一页 ⬆",
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.testSimulateAction(ActionType.SWIPE_DOWN)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionButton(
                            text = "左翻页 ⬅",
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.testSimulateAction(ActionType.SWIPE_RIGHT)
                        }

                        QuickActionButton(
                            text = "右翻页 ➡",
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.testSimulateAction(ActionType.SWIPE_LEFT)
                        }

                        QuickActionButton(
                            text = "中心轻触",
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.testSimulateAction(ActionType.CLICK_CENTER)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EmeraldSuccess,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "去授权", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.primary
        ),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
