package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.AppDatabase
import com.example.data.GestureRepository

class GestureReaderApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var repository: GestureRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(this)
        } catch (_: Exception) {}
        database = AppDatabase.getInstance(this)
        repository = GestureRepository(this, database.gestureDao())
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_OVERLAY_SERVICE,
                "GestureReader 识别服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "在后台和悬浮窗中提供头部与手势实时无障碍识别"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_OVERLAY_SERVICE = "channel_gesture_tracking"
        lateinit var instance: GestureReaderApp
            private set
    }
}
