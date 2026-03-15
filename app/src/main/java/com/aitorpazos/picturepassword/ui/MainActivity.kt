package com.aitorpazos.picturepassword.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
        val previewCard = findViewById<FrameLayout>(R.id.previewCard)
        val placeholderContent = findViewById<LinearLayout>(R.id.placeholderContent)

        setupButton.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        testButton.setOnClickListener {
            startActivity(Intent(this, LockScreenActivity::class.java))
        }

        clearButton.setOnClickListener {
            passwordStore.clear()
            updateUI(statusText, testButton, clearButton, previewImage, previewCard, placeholderContent)
        }

        updateUI(statusText, testButton, clearButton, previewImage, previewCard, placeholderContent)
    }

    override fun onResume() {
        super.onResume()
        val statusText = findViewById<TextView>(R.id.statusText)
        val testButton = findViewById<Button>(R.id.testButton)
        val clearButton = findViewById<Button>(R.id.clearButton)
        val previewImage = findViewById<ImageView>(R.id.previewImage)
        val previewCard = findViewById<FrameLayout>(R.id.previewCard)
        val placeholderContent = findViewById<LinearLayout>(R.id.placeholderContent)
        updateUI(statusText, testButton, clearButton, previewImage, previewCard, placeholderContent)
    }

    private fun updateUI(
        statusText: TextView,
        testButton: Button,
        clearButton: Button,
        previewImage: ImageView,
        previewCard: FrameLayout,
        placeholderContent: LinearLayout
    ) {
        val configured = passwordStore.isConfigured()
        if (configured) {
            statusText.text = "Picture Password is configured ✓"
            testButton.isEnabled = true
            testButton.alpha = 1.0f
            clearButton.isEnabled = true
            clearButton.alpha = 1.0f
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
            showPlaceholder(previewImage, placeholderContent, previewCard)
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
