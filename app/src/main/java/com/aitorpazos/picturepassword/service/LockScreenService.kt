package com.aitorpazos.picturepassword.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.View
import android.view.WindowManager
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
    /** Opaque black overlay added via WindowManager to hide the desktop before the activity appears. */
    private var shieldView: View? = null
    private var windowManager: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
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

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        removeShield()
        unregisterScreenReceiver()
        instance = null
        super.onDestroy()
    }

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        pendingLock = true
                        // Immediately add a black overlay so when the screen turns back on
                        // the user sees black instead of the home screen.
                        addShield()
                        // Also launch the lock activity immediately while screen is off.
                        // It will be ready in the window stack when the screen turns on.
                        // The shield covers any gap.
                        showLockScreen()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        if (pendingLock) {
                            pendingLock = false
                            // Activity was already launched on SCREEN_OFF, but ensure
                            // it's there in case the first launch was too slow.
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

    /**
     * Add a full-screen opaque black overlay via WindowManager.
     * This is added on SCREEN_OFF so it's already covering the screen
     * when the user presses the power button. The LockScreenActivity
     * will call [removeShieldFromActivity] once it has rendered.
     */
    private fun addShield() {
        if (shieldView != null) return // already showing

        val view = View(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE
        )

        try {
            windowManager?.addView(view, params)
            shieldView = view
        } catch (_: Exception) {
            // Overlay permission might be missing — fall back to activity-only
        }
    }

    /** Remove the black shield overlay. */
    internal fun removeShield() {
        shieldView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        shieldView = null
    }

    private fun showLockScreen() {
        // Only show if password is configured and service is enabled
        val passwordStore = PasswordStore(this)
        val settingsStore = SettingsStore(this)

        if (!passwordStore.isConfigured() || !settingsStore.serviceEnabled) {
            removeShield()
            return
        }

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

        /** Singleton reference so the activity can call back to remove the shield. */
        @Volatile
        var instance: LockScreenService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, LockScreenService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LockScreenService::class.java)
            context.stopService(intent)
        }

        /** Called by LockScreenActivity once its first frame has been drawn. */
        fun removeShieldFromActivity() {
            instance?.removeShield()
        }
    }
}
