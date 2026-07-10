package com.coshelper.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.coshelper.MainActivity
import com.coshelper.R

class ChatService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refreshWakeLock = object : Runnable {
        override fun run() {
            wakeLock?.takeIf { it.isHeld }?.acquire(10 * 60 * 1000L)
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MioKig 对讲服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持对讲后台连接"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MioKig 对讲中")
            .setContentText("正在寻找附近设备并等待对讲")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MioKig::ChatService").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 10 minutes, refreshed periodically
        }
        handler.removeCallbacks(refreshWakeLock)
        handler.postDelayed(refreshWakeLock, REFRESH_INTERVAL_MS)
    }

    private fun releaseWakeLock() {
        handler.removeCallbacks(refreshWakeLock)
        wakeLock?.apply {
            if (isHeld) release()
        }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "coshelper_chat_service"
        private const val NOTIFICATION_ID = 1001
        private const val REFRESH_INTERVAL_MS = 9 * 60 * 1000L
    }
}
