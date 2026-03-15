package com.aitorpazos.picturepassword.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aitorpazos.picturepassword.R
import com.aitorpazos.picturepassword.crypto.PasswordStore
import com.aitorpazos.picturepassword.crypto.SettingsStore
import com.aitorpazos.picturepassword.crypto.SettingsStore.ImageSource
import com.aitorpazos.picturepassword.crypto.SettingsStore.UnlockMode
import com.aitorpazos.picturepassword.service.LockScreenService
import com.aitorpazos.picturepassword.ui.setup.SetupActivity
import com.aitorpazos.picturepassword.util.WallpaperHelper

class MainActivity : AppCompatActivity() {

    private lateinit var passwordStore: PasswordStore
    private lateinit var settingsStore: SettingsStore

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> refreshPermissionsUI() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        passwordStore = PasswordStore(this)
        settingsStore = SettingsStore(this)

        // Version hint
        val versionText = findViewById<TextView>(R.id.versionText)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = "v${pInfo.versionName}"
        } catch (_: Exception) {
            versionText.text = ""
        }

        val statusText = findViewById<TextView>(R.id.statusText)
        val setupButton = findViewById<Button>(R.id.setupButton)
        val testButton = findViewById<Button>(R.id.testButton)
        val clearButton = findViewById<Button>(R.id.clearButton)
        val previewImage = findViewById<ImageView>(R.id.previewImage)
        val previewCard = findViewById<FrameLayout>(R.id.previewCard)
        val placeholderContent = findViewById<LinearLayout>(R.id.placeholderContent)

        // Service toggle
        val serviceToggle = findViewById<Button>(R.id.serviceToggleButton)
        val serviceStatus = findViewById<TextView>(R.id.serviceStatusText)

        // Unlock mode radio group
        val unlockModeGroup = findViewById<RadioGroup>(R.id.unlockModeGroup)
        val settingsSection = findViewById<LinearLayout>(R.id.settingsSection)
        val radioPictureOnly = findViewById<RadioButton>(R.id.radioPictureOnly)
        val radioBiometricOnly = findViewById<RadioButton>(R.id.radioBiometricOnly)
        val radioBothRequired = findViewById<RadioButton>(R.id.radioBothRequired)
        val radioEither = findViewById<RadioButton>(R.id.radioEither)

        setupButton.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        testButton.setOnClickListener {
            startActivity(Intent(this, LockScreenActivity::class.java))
        }

        clearButton.setOnClickListener {
            passwordStore.clear()
            settingsStore.serviceEnabled = false
            LockScreenService.stop(this)
            updateUI(statusText, testButton, clearButton, previewImage, previewCard, placeholderContent,
                serviceToggle, serviceStatus, settingsSection)
        }

        // Service toggle
        serviceToggle.setOnClickListener {
            if (settingsStore.serviceEnabled) {
                settingsStore.serviceEnabled = false
                LockScreenService.stop(this)
            } else {
                if (passwordStore.isConfigured()) {
                    settingsStore.serviceEnabled = true
                    LockScreenService.start(this)
                }
            }
            updateServiceUI(serviceToggle, serviceStatus, settingsSection)
        }

        // Unlock mode selection
        when (settingsStore.unlockMode) {
            UnlockMode.PICTURE_ONLY -> radioPictureOnly.isChecked = true
            UnlockMode.BIOMETRIC_ONLY -> radioBiometricOnly.isChecked = true
            UnlockMode.BOTH_REQUIRED -> radioBothRequired.isChecked = true
            UnlockMode.EITHER -> radioEither.isChecked = true
        }

        unlockModeGroup.setOnCheckedChangeListener { _, checkedId ->
            settingsStore.unlockMode = when (checkedId) {
                R.id.radioPictureOnly -> UnlockMode.PICTURE_ONLY
                R.id.radioBiometricOnly -> UnlockMode.BIOMETRIC_ONLY
                R.id.radioBothRequired -> UnlockMode.BOTH_REQUIRED
                R.id.radioEither -> UnlockMode.EITHER
                else -> UnlockMode.EITHER
            }
        }

        // Permission item click handlers
        setupPermissionClickHandlers()

        updateUI(statusText, testButton, clearButton, previewImage, previewCard, placeholderContent,
            serviceToggle, serviceStatus, settingsSection)
    }

    override fun onResume() {
        super.onResume()
        val statusText = findViewById<TextView>(R.id.statusText)
        val testButton = findViewById<Button>(R.id.testButton)
        val clearButton = findViewById<Button>(R.id.clearButton)
        val previewImage = findViewById<ImageView>(R.id.previewImage)
        val previewCard = findViewById<FrameLayout>(R.id.previewCard)
        val placeholderContent = findViewById<LinearLayout>(R.id.placeholderContent)
        val serviceToggle = findViewById<Button>(R.id.serviceToggleButton)
        val serviceStatus = findViewById<TextView>(R.id.serviceStatusText)
        val settingsSection = findViewById<LinearLayout>(R.id.settingsSection)
        updateUI(statusText, testButton, clearButton, previewImage, previewCard, placeholderContent,
            serviceToggle, serviceStatus, settingsSection)
        refreshPermissionsUI()
    }

    private fun setupPermissionClickHandlers() {
        // Overlay permission
        findViewById<View>(R.id.permOverlay).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // Notification permission
        findViewById<View>(R.id.permNotification).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Already granted — open notification settings
                    openAppNotificationSettings()
                }
            } else {
                openAppNotificationSettings()
            }
        }

        // Battery optimization
        findViewById<View>(R.id.permBattery).setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                // Fallback to battery optimization list
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        // Restricted settings (opens app info)
        findViewById<View>(R.id.permRestricted).setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        // System lock (opens security settings)
        findViewById<View>(R.id.permSystemLock).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun refreshPermissionsUI() {
        val permissionsSection = findViewById<LinearLayout>(R.id.permissionsSection)

        // Only show permissions section when service is enabled or password is configured
        val configured = passwordStore.isConfigured()
        if (!configured) {
            permissionsSection.visibility = View.GONE
            return
        }
        permissionsSection.visibility = View.VISIBLE

        // Overlay permission
        val hasOverlay = Settings.canDrawOverlays(this)
        updatePermStatus(R.id.permOverlayStatus, hasOverlay)

        // Notification permission
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        updatePermStatus(R.id.permNotificationStatus, hasNotification)

        // Battery optimization
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val hasBattery = pm.isIgnoringBatteryOptimizations(packageName)
        updatePermStatus(R.id.permBatteryStatus, hasBattery)

        // Restricted settings — we can't directly check this, so we infer from overlay
        // If overlay is granted, restricted settings must have been allowed
        updatePermStatus(R.id.permRestrictedStatus, hasOverlay)
    }

    private fun updatePermStatus(textViewId: Int, granted: Boolean) {
        val tv = findViewById<TextView>(textViewId)
        if (granted) {
            tv.text = "✅"
        } else {
            tv.text = "❌"
        }
    }

    private fun updateUI(
        statusText: TextView,
        testButton: Button,
        clearButton: Button,
        previewImage: ImageView,
        previewCard: FrameLayout,
        placeholderContent: LinearLayout,
        serviceToggle: Button,
        serviceStatus: TextView,
        settingsSection: LinearLayout
    ) {
        val configured = passwordStore.isConfigured()
        if (configured) {
            statusText.text = "Picture Password is configured ✓"
            testButton.isEnabled = true
            testButton.alpha = 1.0f
            clearButton.isEnabled = true
            clearButton.alpha = 1.0f
            serviceToggle.isEnabled = true
            serviceToggle.alpha = 1.0f
            val config = passwordStore.load()
            if (config != null) {
                try {
                    previewImage.setImageURI(config.imageUri)
                    previewImage.visibility = View.VISIBLE
                    placeholderContent.visibility = View.GONE
                    previewCard.setBackgroundResource(R.drawable.bg_image_preview)
                    previewCard.clipToOutline = true
                } catch (_: Exception) {
                    showPlaceholder(previewImage, placeholderContent, previewCard)
                }
            } else {
                showPlaceholder(previewImage, placeholderContent, previewCard)
            }
        } else {
            statusText.text = "No Picture Password configured"
            testButton.isEnabled = false
            testButton.alpha = 0.4f
            clearButton.isEnabled = false
            clearButton.alpha = 0.4f
            serviceToggle.isEnabled = false
            serviceToggle.alpha = 0.4f
            showPlaceholder(previewImage, placeholderContent, previewCard)
        }

        updateServiceUI(serviceToggle, serviceStatus, settingsSection)
        refreshPermissionsUI()
        refreshWallpaperWarning()
    }

    /**
     * Check if the system wallpaper has changed since setup and show/hide the warning banner.
     * Also proactively checks the hash when the user opens the app (not just on SCREEN_ON).
     */
    private fun refreshWallpaperWarning() {
        val banner = findViewById<LinearLayout>(R.id.wallpaperWarningBanner)

        if (!passwordStore.isConfigured() || settingsStore.imageSource != ImageSource.SYSTEM_WALLPAPER) {
            banner.visibility = View.GONE
            return
        }

        // Proactively check hash if not already flagged
        if (!settingsStore.wallpaperChanged) {
            val storedHash = settingsStore.wallpaperHash
            if (storedHash.isNotEmpty() && WallpaperHelper.hasWallpaperChanged(this, storedHash)) {
                settingsStore.wallpaperChanged = true
            }
        }

        banner.visibility = if (settingsStore.wallpaperChanged) View.VISIBLE else View.GONE
    }

    private fun updateServiceUI(
        serviceToggle: Button,
        serviceStatus: TextView,
        settingsSection: LinearLayout
    ) {
        val enabled = settingsStore.serviceEnabled
        if (enabled) {
            serviceToggle.text = "Disable Lock Screen"
            serviceToggle.setBackgroundResource(R.drawable.bg_button_danger)
            serviceToggle.setTextColor(0xAAFF6B6BL.toInt())
            serviceStatus.text = "🛡️ Lock screen service is active"
            serviceStatus.setTextColor(0xFF81C784.toInt())
            settingsSection.visibility = View.VISIBLE
        } else {
            serviceToggle.text = "Enable as Lock Screen"
            serviceToggle.setBackgroundResource(R.drawable.bg_button_primary)
            serviceToggle.setTextColor(0xFFFFFFFF.toInt())
            serviceStatus.text = "Lock screen service is off"
            serviceStatus.setTextColor(0x88FFFFFF.toInt())
            settingsSection.visibility = View.GONE
        }
    }

    private fun showPlaceholder(
        previewImage: ImageView,
        placeholderContent: LinearLayout,
        previewCard: FrameLayout
    ) {
        previewImage.visibility = View.GONE
        placeholderContent.visibility = View.VISIBLE
        previewCard.setBackgroundResource(R.drawable.bg_image_placeholder)
    }
}
