package com.aitorpazos.picturepassword.ui

import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.aitorpazos.picturepassword.R
import com.aitorpazos.picturepassword.crypto.PasswordStore
import com.aitorpazos.picturepassword.crypto.SettingsStore
import com.aitorpazos.picturepassword.crypto.SettingsStore.UnlockMode
import com.aitorpazos.picturepassword.model.NumberGridFactory
import com.aitorpazos.picturepassword.model.PicturePasswordConfig
import com.aitorpazos.picturepassword.model.UnlockVerifier
import com.aitorpazos.picturepassword.service.LockScreenService
import com.aitorpazos.picturepassword.ui.views.NumberGridView

/**
 * Lock screen activity that displays the picture password unlock interface.
 * Shows the user's chosen image with a randomized number grid overlay.
 * The user drags the grid to align their secret number with the secret point.
 *
 * Supports multiple unlock modes:
 * - PICTURE_ONLY: Only picture password unlocks
 * - BIOMETRIC_ONLY: Only biometrics unlocks
 * - BOTH_REQUIRED: Picture password + biometrics both needed (2FA)
 * - EITHER: Either method unlocks
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var passwordStore: PasswordStore
    private lateinit var settingsStore: SettingsStore
    private var config: PicturePasswordConfig? = null
    private var failedAttempts = 0
    private var currentVisibleCols = NumberGridFactory.VISIBLE_COLS

    // 2FA state tracking
    private var pictureUnlocked = false
    private var biometricUnlocked = false
    private var unlockMode = UnlockMode.EITHER
    private var isFromService = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots and screen recording for security
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        isFromService = intent.getBooleanExtra(LockScreenService.EXTRA_FROM_SERVICE, false)

        // Show over lock screen — always needed
        setShowWhenLocked(true)

        // When launched from service (SCREEN_ON), the screen is already on — no need
        // to turn it on. For manual test unlock from the app, turn screen on.
        setTurnScreenOn(!isFromService)

        // Dismiss the system keyguard so our lock screen is the only one visible
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        keyguardManager?.requestDismissKeyguard(this, null)

        setContentView(R.layout.activity_lock_screen)

        passwordStore = PasswordStore(this)
        settingsStore = SettingsStore(this)
        config = passwordStore.load()
        unlockMode = settingsStore.unlockMode

        if (config == null) {
            Toast.makeText(this, "No picture password configured", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val imageView = findViewById<ImageView>(R.id.lockScreenImage)
        val gridView = findViewById<NumberGridView>(R.id.lockScreenGrid)
        val statusText = findViewById<TextView>(R.id.lockStatusText)
        val biometricBtn = findViewById<TextView>(R.id.biometricButton)

        // Set background image
        try {
            imageView.setImageURI(config!!.imageUri)
        } catch (e: Exception) {
            imageView.setImageResource(android.R.color.black)
        }

        // Once the first frame is drawn, remove the black shield overlay from the service.
        // We delay removal slightly to ensure the activity window is fully composited
        // by the window manager, preventing any flash of the home screen.
        val rootView = window.decorView
        rootView.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                rootView.viewTreeObserver.removeOnPreDrawListener(this)
                // Post to next frame — by then the activity window is fully visible
                rootView.post {
                    LockScreenService.removeShieldFromActivity()
                }
                return true
            }
        })

        // Setup based on unlock mode
        when (unlockMode) {
            UnlockMode.BIOMETRIC_ONLY -> {
                // Hide grid, show biometric only
                gridView.visibility = android.view.View.GONE
                statusText.text = "Use biometrics to unlock"
                setupBiometricButton(biometricBtn, statusText)
                // Auto-prompt biometrics
                showBiometricPrompt(statusText)
            }
            else -> {
                // Show grid for PICTURE_ONLY, BOTH_REQUIRED, EITHER
                setupGrid(gridView, statusText)
                setupBiometricButton(biometricBtn, statusText)
            }
        }
    }

    private fun setupGrid(gridView: NumberGridView, statusText: TextView) {
        // Create a fresh random grid with randomized density
        currentVisibleCols = NumberGridFactory.randomVisibleCols()
        gridView.visibleCols = currentVisibleCols
        gridView.numberGrid = NumberGridFactory.createRandomGrid()
        gridView.showTargetPoint = false

        // Update hint based on mode
        statusText.text = when (unlockMode) {
            UnlockMode.BOTH_REQUIRED -> "Touch and drag to unlock (step 1 of 2)"
            else -> "Touch and drag to unlock"
        }

        // Handle grid release — check unlock
        gridView.onGridReleased = { offsetX, offsetY ->
            val currentGrid = gridView.numberGrid
            if (currentGrid != null) {
                val movedGrid = currentGrid.withOffset(offsetX, offsetY)

                val cellSizeNorm = 1f / currentVisibleCols
                val originCol = (currentGrid.cols - currentVisibleCols) / 2f
                val originRow = gridView.computeOriginRow()

                val verifyResult = UnlockVerifier.verify(movedGrid, config!!, cellSizeNorm, originCol, originRow)

                if (verifyResult) {
                    pictureUnlocked = true
                    checkUnlockComplete(statusText)
                } else {
                    failedAttempts++
                    statusText.text = "Incorrect — try again"
                    // Reset grid with new random positions AND new random density
                    currentVisibleCols = NumberGridFactory.randomVisibleCols()
                    gridView.visibleCols = currentVisibleCols
                    gridView.numberGrid = NumberGridFactory.createRandomGrid()
                }
            }
        }
    }

    private fun setupBiometricButton(biometricBtn: TextView, statusText: TextView) {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

        val showBiometric = when (unlockMode) {
            UnlockMode.PICTURE_ONLY -> false
            else -> canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS
        }

        if (showBiometric) {
            biometricBtn.visibility = android.view.View.VISIBLE
            biometricBtn.text = when (unlockMode) {
                UnlockMode.BIOMETRIC_ONLY -> "🔐 Unlock with Biometrics"
                UnlockMode.BOTH_REQUIRED -> "🔐 Biometric verification"
                else -> "🔐 Use Biometrics"
            }
            biometricBtn.setOnClickListener { showBiometricPrompt(statusText) }
        } else {
            biometricBtn.visibility = android.view.View.GONE
        }
    }

    private fun showBiometricPrompt(statusText: TextView) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricUnlocked = true
                    checkUnlockComplete(statusText)
                }

                override fun onAuthenticationFailed() {
                    Toast.makeText(this@LockScreenActivity, "Biometric failed", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock with Biometrics")
            .setSubtitle(when (unlockMode) {
                UnlockMode.BOTH_REQUIRED -> "Biometric verification (part of 2FA)"
                else -> "Use fingerprint or face to unlock"
            })
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun checkUnlockComplete(statusText: TextView) {
        val unlocked = when (unlockMode) {
            UnlockMode.PICTURE_ONLY -> pictureUnlocked
            UnlockMode.BIOMETRIC_ONLY -> biometricUnlocked
            UnlockMode.BOTH_REQUIRED -> pictureUnlocked && biometricUnlocked
            UnlockMode.EITHER -> pictureUnlocked || biometricUnlocked
        }

        if (unlocked) {
            statusText.text = "Unlocked! ✓"
            Toast.makeText(this, "Unlocked!", Toast.LENGTH_SHORT).show()
            LockScreenService.removeShieldFromActivity()
            finish()
        } else if (unlockMode == UnlockMode.BOTH_REQUIRED) {
            // One factor done, prompt for the other
            if (pictureUnlocked && !biometricUnlocked) {
                statusText.text = "Picture correct ✓ — now verify biometrics"
                showBiometricPrompt(statusText)
            } else if (biometricUnlocked && !pictureUnlocked) {
                statusText.text = "Biometric verified ✓ — now drag your number"
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent back press on lock screen when launched from service
        if (isFromService) {
            // Do nothing — can't dismiss
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
