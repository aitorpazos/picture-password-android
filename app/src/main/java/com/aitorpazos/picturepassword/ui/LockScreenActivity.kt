package com.aitorpazos.picturepassword.ui

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.aitorpazos.picturepassword.R
import com.aitorpazos.picturepassword.crypto.PasswordStore
import com.aitorpazos.picturepassword.model.NumberGridFactory
import com.aitorpazos.picturepassword.model.PicturePasswordConfig
import com.aitorpazos.picturepassword.model.UnlockVerifier
import com.aitorpazos.picturepassword.ui.views.NumberGridView

/**
 * Lock screen activity that displays the picture password unlock interface.
 * Shows the user's chosen image with a randomized number grid overlay.
 * The user drags the grid to align their secret number with the secret point.
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var passwordStore: PasswordStore
    private var config: PicturePasswordConfig? = null
    private var failedAttempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        passwordStore = PasswordStore(this)
        config = passwordStore.load()

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

        // Create a fresh random grid
        gridView.numberGrid = NumberGridFactory.createRandomGrid()

        // Handle grid release — check unlock
        gridView.onGridReleased = { offsetX, offsetY ->
            val grid = gridView.numberGrid?.withOffset(offsetX, offsetY)
            if (grid != null && UnlockVerifier.verify(grid, config!!)) {
                statusText.text = "Unlocked! ✓"
                Toast.makeText(this, "Unlocked!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                failedAttempts++
                statusText.text = "Try again ($failedAttempts failed)"
                // Reset grid with new random positions
                gridView.numberGrid = NumberGridFactory.createRandomGrid()

                if (failedAttempts >= 3) {
                    statusText.text = "Too many attempts. Use biometrics or try again."
                }
            }
        }

        // Biometric fallback
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricBtn.visibility = android.view.View.VISIBLE
            biometricBtn.setOnClickListener { showBiometricPrompt() }
        } else {
            biometricBtn.visibility = android.view.View.GONE
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    Toast.makeText(this@LockScreenActivity, "Biometric unlock!", Toast.LENGTH_SHORT).show()
                    finish()
                }

                override fun onAuthenticationFailed() {
                    Toast.makeText(this@LockScreenActivity, "Biometric failed", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock with Biometrics")
            .setSubtitle("Use fingerprint or face to unlock")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent back press on lock screen
        // In a real lock screen replacement, you'd block this entirely
    }
}
