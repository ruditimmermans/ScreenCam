package com.sparklead.screencam.services

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.Nullable
import androidx.core.app.NotificationCompat
import com.sparklead.screencam.ui.activities.RecorderActivity
import com.sparklead.screencam.utils.Constants


class BackgroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val intent1 = Intent(this, RecorderActivity::class.java)
        val pendingIntent1 = PendingIntent.getActivity(this, 0, intent1, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, RecorderActivity::class.java).apply {
            action = Constants.STOP_RECORDING_ACTION
        }
        val stopPendingIntent = PendingIntent.getActivity(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification1: Notification = NotificationCompat.Builder(this, "ScreenRecorder")
            .setSmallIcon(com.sparklead.screencam.R.drawable.screencam)
            .setContentTitle("ScreenCam")
            .setContentText("Recording in progress...")
            .setContentIntent(pendingIntent1)
            .addAction(com.sparklead.screencam.R.drawable.stop, "Stop Recording", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification1, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification1)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ScreenRecorder", "Foreground notification",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager: NotificationManager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopForeground(true)
        stopSelf()
        super.onDestroy()
    }
}