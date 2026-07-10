package com.coshelper.rvc

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
import com.coshelper.data.AudioSettingsRepository

class RvcForegroundService : Service() {

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()

        val repo = AudioSettingsRepository(this)
        val manager = RvcManager.getInstance(this)
        manager.setInputDevice(repo.getInputDevice("rvc"))
        manager.setOutputDevice(repo.getOutputDevice("rvc"))
        manager.start()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        RvcManager.getInstance(this).cleanup()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MioKig 变声器",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持实时变声后台运行"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MioKig 变声器运行中")
            .setContentText("正在后台实时变声")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MioKig::RvcForegroundService"
        ).apply {
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
        private const val CHANNEL_ID = "coshelper_rvc_service"
        private const val NOTIFICATION_ID = 1002
        private const val REFRESH_INTERVAL_MS = 9 * 60 * 1000L
    }
}
