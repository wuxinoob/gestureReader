package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.GestureReaderApp
import com.example.MainActivity
import com.example.R
import com.example.model.ActionType
import com.example.model.PerformanceMetrics
import com.example.model.RecognitionResult
import com.example.model.TriggerGesture
import com.example.model.UserConfig
import com.example.vision.VisionProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

class OverlayTrackingService : Service(), LifecycleOwner {

    private val lifecycleRegistry: LifecycleRegistry by lazy { LifecycleRegistry(this) }
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var windowManager: WindowManager? = null
    private var overlayRootView: View? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    private var visionProcessor: VisionProcessor? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var previewContainer: View? = null
    private var previewView: PreviewView? = null
    private var tvStatus: TextView? = null
    private var tvFps: TextView? = null
    private var tvLatency: TextView? = null
    private var tvGesture: TextView? = null
    private var tvTelemetry: TextView? = null
    private var progressTrigger: ProgressBar? = null
    private var btnMinimize: View? = null
    private var btnPause: ImageView? = null
    private var panelExpanded: View? = null
    private var panelBubble: View? = null
    private var bubbleIcon: ImageView? = null
    private var statusDot: View? = null
    private var tvBubbleBadge: TextView? = null
    private var bubbleBgDrawable: GradientDrawable? = null

    // 默认以极简悬浮小球模式呈现，日常阅读不遮挡视线
    private var isMinimized = true
    private var currentConfig: UserConfig = UserConfig()

    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var currentRotation: Int = Surface.ROTATION_0
    private var orientationEventListener: OrientationEventListener? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var screenStateReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        if (!serviceJob.isActive) {
            serviceJob = SupervisorJob()
            serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        instance = this
        _isServiceRunning.value = true

        startForegroundNotification()

        visionProcessor = VisionProcessor(this) { gesture, action ->
            handleActionTriggered(gesture, action)
        }

        setupRotationAndScreenTracking()
        initOverlayView()
        observeData()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        startCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> {
                togglePauseState()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun createResidentNotification(status: String? = null): Notification {
        val repo = GestureReaderApp.instance.repository
        val isPaused = repo.configState.value.isPaused

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val togglePauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayTrackingService::class.java).apply {
                this.action = ACTION_TOGGLE_PAUSE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, OverlayTrackingService::class.java).apply {
                this.action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (isPaused) "GestureReader · 辅助阅读已暂停" else "GestureReader · 辅助阅读运行中"
        val content = status ?: if (isPaused) "轻触悬浮球即可一键恢复翻页识别" else "正在实时监测头部姿态与手势，可在任意阅读器中翻页"

        return NotificationCompat.Builder(this, GestureReaderApp.CHANNEL_OVERLAY_SERVICE)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_floating_reading)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "开启识别" else "暂停识别",
                togglePauseIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止服务",
                stopIntent
            )
            .build()
    }

    private fun startForegroundNotification() {
        val notification = createResidentNotification()
        val hasCamera = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val primaryType = if (hasCamera) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, primaryType)
                return
            } catch (e: Exception) {
                Log.w(TAG, "startForeground with primaryType failed: ${e.message}, retrying specialUse")
            }

            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                return
            } catch (e: Exception) {
                Log.w(TAG, "startForeground with specialUse failed: ${e.message}, falling back")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val type = if (hasCamera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
                if (type != 0) {
                    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "startForeground Q failed: ${e.message}")
            }
        }

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "All startForeground attempts failed: ${e.message}")
        }
    }

    private fun updateForegroundNotification(status: String? = null) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, createResidentNotification(status))
        } catch (e: Exception) {
            Log.e(TAG, "updateForegroundNotification error: ${e.message}")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initOverlayView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted; running in background without overlay view")
            return
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 关键配置：开启 FLAG_HARDWARE_ACCELERATED，防止 TextureView 摄像头画面黑屏
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 260
        }
        windowLayoutParams = params

        overlayRootView = buildOverlayViewTree()

        try {
            windowManager?.addView(overlayRootView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view: ${e.message}")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildOverlayViewTree(): View {
        val dp = resources.displayMetrics.density
        fun dpToPx(d: Int) = (d * dp).toInt()
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        val root = FrameLayout(this)

        // 1. 展开面板 (现代化暗黑玻璃卡片圆角风格)
        val panelBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(18).toFloat()
            setColor(0xF70F172A.toInt()) // 深色半透明玻璃
            setStroke(dpToPx(1), 0x3338BDF8.toInt()) // 科技蓝细边框
        }

        val expandedLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            background = panelBg
            elevation = dpToPx(12).toFloat()
            alpha = 0.95f
            visibility = if (isMinimized) View.GONE else View.VISIBLE
        }
        panelExpanded = expandedLayout

        // 顶部标题栏 + 拖动手柄 + 按钮组
        val headerLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(8))
        }

        val tvTitle = TextView(this).apply {
            text = "辅助阅读控制台 ⠿"
            setTextColor(0xFF38BDF8.toInt())
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // 打开主应用界面图标
        val openAppIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_agenda)
            setColorFilter(0xFF38BDF8.toInt())
            setPadding(dpToPx(5), dpToPx(5), dpToPx(5), dpToPx(5))
            setOnClickListener {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                if (launchIntent != null) startActivity(launchIntent)
            }
        }

        val pauseIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(0xFFE2E8F0.toInt())
            setPadding(dpToPx(5), dpToPx(5), dpToPx(5), dpToPx(5))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                togglePauseState()
            }
        }
        btnPause = pauseIcon

        val minIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(0xFF94A3B8.toInt())
            setPadding(dpToPx(5), dpToPx(5), dpToPx(5), dpToPx(5))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                toggleMinimize(true)
            }
        }
        btnMinimize = minIcon

        headerLayout.addView(tvTitle)
        headerLayout.addView(openAppIcon)
        headerLayout.addView(pauseIcon)
        headerLayout.addView(minIcon)
        expandedLayout.addView(headerLayout)

        // 头部标题栏专用拖动监听器
        var startX = 0
        var startY = 0
        var touchDownX = 0f
        var touchDownY = 0f
        var isHeaderDragging = false

        headerLayout.setOnTouchListener { _, event ->
            val params = windowLayoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    isHeaderDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchDownX).toInt()
                    val dy = (event.rawY - touchDownY).toInt()
                    if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isHeaderDragging = true
                    }
                    if (isHeaderDragging) {
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager?.updateViewLayout(overlayRootView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }

        // 前置小窗口摄像头容器 (含 AR 扫描边框)
        val pContainer = FrameLayout(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(dpToPx(136), dpToPx(104)).apply {
                topMargin = dpToPx(4)
                bottomMargin = dpToPx(4)
            }
            visibility = if (currentConfig.showCameraPreview) View.VISIBLE else View.GONE
        }
        previewContainer = pContainer

        val preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        previewView = preview
        pContainer.addView(preview)

        // AR 扫描边框
        val hudOverlay = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            val borderDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), 0x5038BDF8.toInt())
            }
            background = borderDrawable
        }
        pContainer.addView(hudOverlay)
        expandedLayout.addView(pContainer)

        // 实时识别数值监测栏 (Live Telemetry)
        val telemetry = TextView(this).apply {
            text = "姿态: 仰0° 偏0° | 视线: 居中"
            setTextColor(0xFF38BDF8.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(2), 0, dpToPx(2))
        }
        tvTelemetry = telemetry
        expandedLayout.addView(telemetry)

        // 当前识别手势与进度条
        val gestureText = TextView(this).apply {
            text = "等待识别..."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(2), 0, dpToPx(2))
        }
        tvGesture = gestureText
        expandedLayout.addView(gestureText)

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(6)
            ).apply {
                topMargin = dpToPx(2)
                bottomMargin = dpToPx(2)
            }
        }
        progressTrigger = progressBar
        expandedLayout.addView(progressBar)

        // 状态信息
        val statusText = TextView(this).apply {
            text = "正在检测..."
            setTextColor(0xFF94A3B8.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
        }
        tvStatus = statusText
        expandedLayout.addView(statusText)

        // 姿态零点校准：以当前舒适阅读姿势标定为中心 0°
        val btnZeroPoint = android.widget.Button(this).apply {
            text = "🎯 以当前姿态为零点"
            textSize = 10.5f
            setTextColor(0xFF0F172A.toInt())
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8).toFloat()
                setColor(0xFF10B981.toInt())
            }
            background = btnBg
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(32)
            ).apply {
                topMargin = dpToPx(3)
                bottomMargin = dpToPx(2)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                calibrateCurrentPostureAsZeroPoint()
            }
        }
        expandedLayout.addView(btnZeroPoint)

        // 核心功能：根据当前摄像头画面识别值，一键自动校准优化阈值
        val btnCalibrate = android.widget.Button(this).apply {
            text = "📐 采样当前动作优化阈值"
            textSize = 10.5f
            setTextColor(0xFF0F172A.toInt())
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8).toFloat()
                setColor(0xFF38BDF8.toInt())
            }
            background = btnBg
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(32)
            ).apply {
                topMargin = dpToPx(2)
                bottomMargin = dpToPx(2)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                calibrateCurrentActionToThreshold()
            }
        }
        expandedLayout.addView(btnCalibrate)

        // 性能指标行
        val metricsRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(3), 0, 0)
        }
        val fpsText = TextView(this).apply {
            text = "-- FPS"
            setTextColor(0xFF10B981.toInt())
            textSize = 10f
            setPadding(0, 0, dpToPx(8), 0)
        }
        tvFps = fpsText

        val latencyText = TextView(this).apply {
            text = "-- ms"
            setTextColor(0xFFF59E0B.toInt())
            textSize = 10f
        }
        tvLatency = latencyText

        metricsRow.addView(fpsText)
        metricsRow.addView(latencyText)
        expandedLayout.addView(metricsRow)

        // 2. 现代极简圆形悬浮球 (Sleek Floating Assistive Touch Ball)
        val ballSize = dpToPx(54)
        val bubbleBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xF20F172A.toInt()) // 深色深邃磨砂质感
            setStroke(dpToPx(2), 0xFF06B6D4.toInt()) // 科技荧光青外圈光环
        }
        bubbleBgDrawable = bubbleBg

        val bubbleLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ballSize, ballSize)
            background = bubbleBg
            elevation = dpToPx(10).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
            visibility = if (isMinimized) View.VISIBLE else View.GONE
        }
        panelBubble = bubbleLayout

        // 精致翻书/阅读图标
        val bIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_floating_reading)
            setColorFilter(0xFF38BDF8.toInt())
            val p = FrameLayout.LayoutParams(dpToPx(24), dpToPx(24), Gravity.CENTER).apply {
                bottomMargin = dpToPx(3)
            }
            layoutParams = p
        }
        bubbleIcon = bIcon

        // 右上角微光状态指示小圆点 (LED 灵动指示灯)
        val dotBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF10B981.toInt())
        }
        val dot = View(this).apply {
            background = dotBg
            val p = FrameLayout.LayoutParams(dpToPx(7), dpToPx(7), Gravity.TOP or Gravity.END).apply {
                topMargin = dpToPx(7)
                rightMargin = dpToPx(7)
            }
            layoutParams = p
        }
        statusDot = dot

        // 底部细致微型胶囊徽章文本
        val bubbleBadge = TextView(this).apply {
            text = "ON"
            setTextColor(0xFF10B981.toInt())
            textSize = 8.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val p = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dpToPx(4)
            }
            layoutParams = p
        }
        tvBubbleBadge = bubbleBadge

        bubbleLayout.addView(bIcon)
        bubbleLayout.addView(dot)
        bubbleLayout.addView(bubbleBadge)

        // 悬浮小球触摸、拖动、微动反馈、单击开关与长按展开监听器
        var bubbleStartX = 0
        var bubbleStartY = 0
        var bubbleTouchX = 0f
        var bubbleTouchY = 0f
        var bubbleTouchTime = 0L
        var isBubbleDragging = false

        bubbleLayout.setOnTouchListener { view, event ->
            val params = windowLayoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下微缩动效
                    view.animate().scaleX(0.91f).scaleY(0.91f).setDuration(80).start()
                    bubbleStartX = params.x
                    bubbleStartY = params.y
                    bubbleTouchX = event.rawX
                    bubbleTouchY = event.rawY
                    bubbleTouchTime = System.currentTimeMillis()
                    isBubbleDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - bubbleTouchX).toInt()
                    val dy = (event.rawY - bubbleTouchY).toInt()
                    if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isBubbleDragging = true
                    }
                    if (isBubbleDragging) {
                        params.x = bubbleStartX + dx
                        params.y = bubbleStartY + dy
                        windowManager?.updateViewLayout(overlayRootView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 弹起动效恢复
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    val duration = System.currentTimeMillis() - bubbleTouchTime
                    val dx = (event.rawX - bubbleTouchX).toInt()
                    val dy = (event.rawY - bubbleTouchY).toInt()
                    val dist = hypot(dx.toDouble(), dy.toDouble())

                    if (!isBubbleDragging && dist < touchSlop) {
                        if (duration >= 600) {
                            // 长按 (>600ms)：展开详细控制器与参数监控
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            toggleMinimize(false)
                            Toast.makeText(this@OverlayTrackingService, "已展开详细控制面板", Toast.LENGTH_SHORT).show()
                        } else {
                            // 单击 (<600ms)：快速开启/暂停辅助阅读功能
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            val isAccEnabled = GestureAccessibilityService.isAccessibilityEnabled(this@OverlayTrackingService)
                            if (!isAccEnabled) {
                                Toast.makeText(this@OverlayTrackingService, "⚠️ 无障碍服务未开启，正在前往设置授权...", Toast.LENGTH_LONG).show()
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                startActivity(intent)
                            } else {
                                val repo = GestureReaderApp.instance.repository
                                val currentlyPaused = repo.configState.value.isPaused
                                togglePauseState()
                                if (currentlyPaused) {
                                    Toast.makeText(this@OverlayTrackingService, "🟢 辅助阅读已开启 · 识别运行中", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@OverlayTrackingService, "⚪ 辅助阅读已暂停", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }

        root.addView(expandedLayout)
        root.addView(bubbleLayout)

        return root
    }

    private fun calibrateCurrentPostureAsZeroPoint() {
        val result = _currentRecognition.value
        val repo = GestureReaderApp.instance.repository
        val curConfig = repo.configState.value
        val newPitch = (curConfig.baselinePitch + result.headPitch).coerceIn(-60f, 60f)
        val newYaw = (curConfig.baselineYaw + result.headYaw).coerceIn(-60f, 60f)
        val newRoll = (curConfig.baselineRoll + result.headRoll).coerceIn(-60f, 60f)

        val newGazeX = if (result.isGazeTracked) {
            (curConfig.baselineGazeX + result.gazeOffsetX).coerceIn(-0.6f, 0.6f)
        } else curConfig.baselineGazeX

        val newGazeY = if (result.isGazeTracked) {
            (curConfig.baselineGazeY + result.gazeOffsetY).coerceIn(-0.6f, 0.6f)
        } else curConfig.baselineGazeY

        serviceScope.launch {
            repo.updateConfig {
                it.copy(
                    baselinePitch = newPitch,
                    baselineYaw = newYaw,
                    baselineRoll = newRoll,
                    baselineGazeX = newGazeX,
                    baselineGazeY = newGazeY
                )
            }
        }
        Toast.makeText(this, "🎯 已将当前姿态与视线标定为中心零点", Toast.LENGTH_SHORT).show()
    }

    private fun calibrateCurrentActionToThreshold() {
        val result = _currentRecognition.value
        val repo = GestureReaderApp.instance.repository
        serviceScope.launch {
            val absYaw = abs(result.headYaw)
            val absPitch = abs(result.headPitch)
            val absGazeX = abs(result.gazeOffsetX)

            var message = "已分析当前画面："
            repo.updateConfig { cur ->
                var updated = cur
                if (absYaw > 7f) {
                    val newYaw = (absYaw * 0.88f).coerceIn(9f, 35f)
                    updated = updated.copy(yawThreshold = newYaw)
                    message += "偏航阈值->${newYaw.toInt()}° "
                }
                if (absPitch > 5f) {
                    val newPitch = (absPitch * 0.88f).coerceIn(7f, 30f)
                    updated = updated.copy(pitchThreshold = newPitch)
                    message += "仰俯阈值->${newPitch.toInt()}° "
                }
                if (absGazeX > 0.10f) {
                    val newGaze = (absGazeX * 0.85f).coerceIn(0.15f, 0.40f)
                    updated = updated.copy(gazeHorizontalThreshold = newGaze)
                    message += "视线阈值->${(newGaze * 100).toInt()}% "
                }
                updated
            }
            if (message == "已分析当前画面：") {
                Toast.makeText(this@OverlayTrackingService, "当前动作幅度较小，请做出偏头或看边动作后再次点击", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@OverlayTrackingService, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleMinimize(minimize: Boolean) {
        isMinimized = minimize
        if (minimize) {
            panelExpanded?.visibility = View.GONE
            panelBubble?.visibility = View.VISIBLE
        } else {
            panelExpanded?.visibility = View.VISIBLE
            panelBubble?.visibility = View.GONE
        }
        overlayRootView?.let { windowManager?.updateViewLayout(it, windowLayoutParams) }
        if (cameraProvider != null && !currentConfig.isPaused) {
            bindCameraUseCases()
        }
    }

    private fun togglePauseState() {
        val repo = GestureReaderApp.instance.repository
        serviceScope.launch {
            repo.updateConfig { cur ->
                val newPaused = !cur.isPaused
                cur.copy(isPaused = newPaused)
            }
        }
    }

    private fun observeData() {
        val repo = GestureReaderApp.instance.repository
        val dp = resources.displayMetrics.density
        fun dpToPx(d: Int) = (d * dp).toInt()

        // 观察配置变更
        serviceScope.launch {
            repo.configState.collectLatest { config ->
                val oldPaused = currentConfig.isPaused
                val oldShowPreview = currentConfig.showCameraPreview
                val oldResolution = currentConfig.resolutionTier
                currentConfig = config

                visionProcessor?.updateConfig(config)
                panelExpanded?.alpha = config.overlayAlpha
                previewContainer?.visibility = if (config.showCameraPreview) View.VISIBLE else View.GONE

                btnPause?.setImageResource(
                    if (config.isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
                )

                // 悬浮球外观动态主题更新
                if (config.isPaused) {
                    bubbleBgDrawable?.setStroke(dpToPx(1.5f.toInt()), 0xFF64748B.toInt())
                    bubbleBgDrawable?.setColor(0xDD1E293B.toInt())
                    bubbleIcon?.setColorFilter(0xFF94A3B8.toInt())
                    statusDot?.visibility = View.GONE
                    tvBubbleBadge?.text = "OFF"
                    tvBubbleBadge?.setTextColor(0xFF94A3B8.toInt())
                } else {
                    bubbleBgDrawable?.setStroke(dpToPx(2), 0xFF06B6D4.toInt())
                    bubbleBgDrawable?.setColor(0xF20F172A.toInt())
                    bubbleIcon?.setColorFilter(0xFF38BDF8.toInt())
                    statusDot?.visibility = View.VISIBLE
                    tvBubbleBadge?.text = "ON"
                    tvBubbleBadge?.setTextColor(0xFF10B981.toInt())
                }

                // 功耗与显示优化：若暂停状态、摄像头预览开关或分辨率发生改变，动态重绑或关闭 Camera
                if ((oldPaused != config.isPaused || oldShowPreview != config.showCameraPreview || oldResolution != config.resolutionTier) && cameraProvider != null) {
                    bindCameraUseCases()
                }

                updateForegroundNotification()
            }
        }

        // 观察手势绑定
        serviceScope.launch {
            repo.allBindings.collectLatest { list ->
                val map = list.mapNotNull { entity ->
                    val g = entity.toGesture() ?: return@mapNotNull null
                    g to Pair(entity.toAction(), entity.isEnabled)
                }.toMap()
                visionProcessor?.updateBindings(map)
            }
        }

        // 观察识别结果更新悬浮窗 UI
        visionProcessor?.let { vp ->
            serviceScope.launch {
                vp.recognitionResult.collectLatest { result ->
                    _currentRecognition.value = result
                    tvStatus?.text = result.statusMessage
                    progressTrigger?.progress = (result.triggerHoldProgress * 100).toInt()

                    // 更新实时 Telemetry 显示
                    val p = result.headPitch.toInt()
                    val y = result.headYaw.toInt()
                    val gx = (result.gazeOffsetX * 100).toInt()
                    tvTelemetry?.text = "实测: 俯仰${p}° | 偏航${y}° | 视线X:${gx}%"

                    val candidate = result.candidateGesture
                    if (candidate != null) {
                        tvGesture?.text = "🎯 ${candidate.displayName}"
                    } else if (result.lastFiredGesture != null) {
                        val elapsed = System.currentTimeMillis() - result.lastFiredTimestamp
                        if (elapsed < 1200) {
                            tvGesture?.text = "✅ 已触发: ${result.lastFiredGesture.displayName}"
                        } else {
                            tvGesture?.text = "正在检测..."
                        }
                    } else {
                        tvGesture?.text = "正在检测..."
                    }
                }
            }

            serviceScope.launch {
                vp.performanceMetrics.collectLatest { metrics ->
                    _currentMetrics.value = metrics
                    tvFps?.text = "${metrics.fps} FPS"
                    tvLatency?.text = "${metrics.inferenceTimeMs} ms"
                }
            }
        }
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Camera permission not granted; startCamera deferred")
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Error acquiring CameraProvider: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun getDeviceRotation(): Int {
        return try {
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val defaultDisplay = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            defaultDisplay?.rotation ?: Surface.ROTATION_0
        } catch (_: Exception) {
            Surface.ROTATION_0
        }
    }

    private fun setupRotationAndScreenTracking() {
        try {
            currentRotation = getDeviceRotation()

            // 1. 注册屏幕显示方向变化监听
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            displayListener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {}
                override fun onDisplayRemoved(displayId: Int) {}
                override fun onDisplayChanged(displayId: Int) {
                    checkAndUpdateRotation()
                }
            }
            displayManager?.registerDisplayListener(displayListener, null)

            // 2. 硬件传感器重力方向监听器 (覆盖所有平板与手机的横竖屏旋转)
            orientationEventListener = object : OrientationEventListener(this) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return
                    val calculatedRotation = when {
                        orientation >= 315 || orientation < 45 -> Surface.ROTATION_0
                        orientation in 45..134 -> Surface.ROTATION_270
                        orientation in 135..224 -> Surface.ROTATION_180
                        orientation in 225..314 -> Surface.ROTATION_90
                        else -> Surface.ROTATION_0
                    }
                    if (calculatedRotation != currentRotation) {
                        applyNewRotation(calculatedRotation)
                    }
                }
            }
            if (orientationEventListener?.canDetectOrientation() == true) {
                orientationEventListener?.enable()
            }

            // 3. 息屏节电广播监听 (锁屏即关摄像头硬件，实现 0% 待机功耗)
            screenStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> {
                            if (currentConfig.screenOffPause) {
                                Log.d(TAG, "Screen off detected: releasing camera hardware for zero power standby")
                                try {
                                    cameraProvider?.unbindAll()
                                    visionProcessor?.pause()
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error pausing on screen off: ${e.message}")
                                }
                            }
                        }
                        Intent.ACTION_SCREEN_ON -> {
                            if (currentConfig.screenOffPause && !currentConfig.isPaused) {
                                Log.d(TAG, "Screen on detected: restoring camera hardware")
                                visionProcessor?.resume()
                                checkAndUpdateRotation()
                                bindCameraUseCases()
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ContextCompat.registerReceiver(
                this,
                screenStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.w(TAG, "setupRotationAndScreenTracking non-fatal error: ${e.message}")
        }
    }

    private fun checkAndUpdateRotation() {
        val newRotation = getDeviceRotation()
        if (newRotation != currentRotation) {
            applyNewRotation(newRotation)
        }
    }

    private fun applyNewRotation(newRotation: Int) {
        currentRotation = newRotation
        try {
            imageAnalysis?.targetRotation = currentRotation
            preview?.targetRotation = currentRotation
            visionProcessor?.onDeviceRotationChanged(currentRotation)
            Log.i(TAG, "Device rotation applied: $currentRotation, updated targetRotation dynamically")
        } catch (e: Exception) {
            Log.w(TAG, "Error applying new rotation: ${e.message}")
        }
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()

            // 核心功耗优化：若当前处于暂停状态，彻底释放并注销摄像头硬件，达到后台 0 功耗
            if (currentConfig.isPaused) {
                Log.d(TAG, "Tracking paused: camera completely unbound to achieve zero power consumption")
                return
            }

            currentRotation = getDeviceRotation()

            // 优先使用前置摄像头，若在部分模拟器或特殊设备上无前置摄像头，则优雅降级到后置或默认
            val cameraSelector = when {
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                else -> CameraSelector.Builder().build()
            }

            // 轻量分辨率适配 (480x360 或 640x480)，避免默认 1080p 导致的高负载与发热
            val targetSize = when (currentConfig.resolutionTier) {
                "LOW" -> android.util.Size(480, 360)
                "HIGH" -> android.util.Size(1280, 720)
                else -> android.util.Size(640, 480)
            }

            // 图像核心分析管道 (无 UI 依赖，静默高效处理动作)
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(targetSize)
                .setTargetRotation(currentRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        visionProcessor?.processImage(imageProxy, serviceScope)
                    }
                }

            val isPreviewVisible = !isMinimized && currentConfig.showCameraPreview && previewView != null && panelExpanded?.visibility == View.VISIBLE
            if (isPreviewVisible) {
                // 面板展开且用户开启取景窗时，绑定到真实可见的 PreviewView 进行实时画面渲染
                preview = Preview.Builder()
                    .setTargetRotation(currentRotation)
                    .build().also {
                        it.setSurfaceProvider(previewView!!.surfaceProvider)
                    }
                provider.bindToLifecycle(this, cameraSelector, preview!!, imageAnalysis!!)
            } else {
                // 默认悬浮小球与后台状态：仅绑定 ImageAnalysis。
                // 内存零排队，帧处理完毕由 ImageProxy.close() 立即回收，彻底根除 Surface 未消费导致的 BufferQueue 占满崩溃！
                provider.bindToLifecycle(this, cameraSelector, imageAnalysis!!)
            }
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun handleActionTriggered(gesture: TriggerGesture, action: ActionType) {
        if (action == ActionType.TOGGLE_TRACKING) {
            Log.d(TAG, "Ignoring TOGGLE_TRACKING from gesture recognition to prevent accidental shutdown")
            return
        }

        // 尝试通过 AccessibilityService 执行手势模拟
        val accService = GestureAccessibilityService.instance
        if (accService != null) {
            accService.performAction(action)
        } else {
            Log.w(TAG, "AccessibilityService is not connected yet")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            orientationEventListener?.disable()
            displayListener?.let {
                val dm = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                dm?.unregisterDisplayListener(it)
            }
            screenStateReceiver?.let { unregisterReceiver(it) }
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        try {
            cameraExecutor.shutdown()
        } catch (_: Exception) {}
        visionProcessor?.close()
        serviceScope.cancel()

        try {
            overlayRootView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        instance = null
        _isServiceRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "OverlayTrackingService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_TOGGLE_PAUSE = "com.example.service.ACTION_TOGGLE_PAUSE"
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP_SERVICE"

        var instance: OverlayTrackingService? = null
            private set

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _currentRecognition = MutableStateFlow(RecognitionResult())
        val currentRecognition: StateFlow<RecognitionResult> = _currentRecognition.asStateFlow()

        private val _currentMetrics = MutableStateFlow(PerformanceMetrics())
        val currentMetrics: StateFlow<PerformanceMetrics> = _currentMetrics.asStateFlow()
    }
}
