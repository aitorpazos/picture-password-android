package com.aitorpazos.picturepassword.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aitorpazos.picturepassword.R
import com.aitorpazos.picturepassword.crypto.PasswordStore
import com.aitorpazos.picturepassword.ui.setup.SetupActivity

class MainActivity : AppCompatActivity() {

    private lateinit var passwordStore: PasswordStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        passwordStore = PasswordStore(this)

        val statusText = findViewById<TextView>(R.id.statusText)
        val setupButton = findViewById<Button>(R.id.setupButton)
        val testButton = findViewById<Button>(R.id.testButton)
        val clearButton = findViewById<Button>(R.id.clearButton)
        val previewImage = findViewById<ImageView>(R.id.previewImage)

        setupButton.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        testButton.setOnClickListener {
            startActivity(Intent(this, LockScreenActivity::class.java))
        }

        clearButton.setOnClickListener {
            passwordStore.clear()
            updateUI(statusText, testButton, clearButton, previewImage)
        }

        updateUI(statusText, testButton, clearButton, previewImage)
    }

    override fun onResume() {
        super.onResume()
        val statusText = findViewById<TextView>(R.id.statusText)
        val testButton = findViewById<Button>(R.id.testButton)
        val clearButton = findViewById<Button>(R.id.clearButton)
        val previewImage = findViewById<ImageView>(R.id.previewImage)
        updateUI(statusText, testButton, clearButton, previewImage)
    }

    private fun updateUI(
        statusText: TextView,
        testButton: Button,
        clearButton: Button,
        previewImage: ImageView
    ) {
        val configured = passwordStore.isConfigured()
        if (configured) {
            statusText.text = "Picture Password is configured ✓"
            testButton.isEnabled = true
            clearButton.isEnabled = true
            val config = passwordStore.load()
            if (config != null) {
                try {
                    previewImage.setImageURI(config.imageUri)
                } catch (_: Exception) {
                    previewImage.setImageResource(android.R.color.darker_gray)
                }
            }
        } else {
            statusText.text = "No Picture Password configured"
            testButton.isEnabled = false
            clearButton.isEnabled = false
            previewImage.setImageResource(android.R.color.darker_gray)
        }
    }
}
