package com.viceside.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.russia.game.R

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "viceside_downloader"
        const val NOTIFICATION_ID = 1001
    }

    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    private val binder = LocalBinder()
    var updater: DataUpdater? = null
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Menyiapkan unduhan data...", 0))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun updateProgress(detail: String, percent: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(detail, percent))
    }

    private fun buildNotification(detail: String, percent: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vice Side Mobile")
            .setContentText(detail)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vice Side Downloader",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi unduhan dan pembaruan data game"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        updater?.cancel()
        super.onDestroy()
    }
}
