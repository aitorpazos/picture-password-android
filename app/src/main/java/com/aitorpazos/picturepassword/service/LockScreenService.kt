package com.aitorpazos.picturepassword.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aitorpazos.picturepassword.R
import com.aitorpazos.picturepassword.crypto.PasswordStore
import com.aitorpazos.picturepassword.crypto.SettingsStore
import com.aitorpazos.picturepassword.ui.LockScreenActivity
import com.aitorpazos.picturepassword.ui.MainActivity

class LockScreenService : Service() {

    private var screenReceiver: BroadcastReceiver? = null
    /** True while the screen is off — we show the lock on the next SCREEN_ON. */
    private var pendingLock = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val openMainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openMainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Picture Password")
            .setContentText("Lock screen protection active")
            .setSmallIcon(R.drawable.ic_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        super.onDestroy()
    }

    private fun registerScreenReceiver() {
        // Strategy: mark a pending lock on SCREEN_OFF, then show the lock activity
        // only on SCREEN_ON (user pressed power button). This avoids any startActivity
        // call while the screen is off, which on many OEMs causes a spurious wake-up.
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        pendingLock = true
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        if (pendingLock) {
                            pendingLock = false
                            showLockScreen()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        screenReceiver = null
    }

    private fun showLockScreen() {
        // Only show if password is configured and service is enabled
        val passwordStore = PasswordStore(this)
        val settingsStore = SettingsStore(this)

        if (!passwordStore.isConfigured() || !settingsStore.serviceEnabled) return

        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(EXTRA_FROM_SERVICE, true)
        }
        startActivity(lockIntent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Picture Password Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the picture password lock screen active"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "picture_password_service"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_FROM_SERVICE = "from_lock_service"

        fun start(context: Context) {
            val intent = Intent(context, LockScreenService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LockScreenService::class.java)
            context.stopService(intent)
        }
    }
}
