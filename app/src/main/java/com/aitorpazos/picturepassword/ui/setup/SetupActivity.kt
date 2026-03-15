package com.aitorpazos.picturepassword.ui.setup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aitorpazos.picturepassword.R
import com.aitorpazos.picturepassword.crypto.PasswordStore
import com.aitorpazos.picturepassword.crypto.SettingsStore
import com.aitorpazos.picturepassword.crypto.SettingsStore.ImageSource
import com.aitorpazos.picturepassword.model.NumberGridFactory
import com.aitorpazos.picturepassword.model.PicturePasswordConfig
import com.aitorpazos.picturepassword.ui.views.NumberGridView
import com.aitorpazos.picturepassword.util.WallpaperHelper

class SetupActivity : AppCompatActivity() {

    private lateinit var passwordStore: PasswordStore
    private lateinit var settingsStore: SettingsStore

    private var selectedImageUri: Uri? = null
    private var selectedImageSource: ImageSource = ImageSource.CUSTOM
    private var selectedNumber: Int = -1
    private var secretX: Float = -1f
    private var secretY: Float = -1f
    private var setupStep = 0 // 0=pick image source, 1=pick number, 2=pick location, 3=confirm

    /** Permission launcher for wallpaper/media access */
    private val wallpaperPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Permission granted — try wallpaper again
            tryUseSystemWallpaper()
        } else {
            Toast.makeText(
                this,
                "Media permission denied. Cannot read system wallpaper.\nGo to Settings → Apps → Picture Password → Permissions → Photos and videos.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Take persistent permission
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            selectedImageUri = uri
            selectedImageSource = ImageSource.CUSTOM
            advanceToStep(1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots and screen recording for security
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_setup)

        passwordStore = PasswordStore(this)
        settingsStore = SettingsStore(this)

        advanceToStep(0)
    }

    private fun advanceToStep(step: Int) {
        try {
            setupStep = step
            val instructionText = findViewById<TextView>(R.id.setupInstructionText)
            val imageView = findViewById<ImageView>(R.id.setupImageView)
            val gridView = findViewById<NumberGridView>(R.id.setupGridView)
            val actionButton = findViewById<Button>(R.id.setupActionButton)
            val numberButtonsContainer = findViewById<LinearLayout>(R.id.numberButtonsContainer)

            when (step) {
                0 -> { // Pick image source
                    instructionText.text = "Step 1: Choose a picture for your lock screen"
                    imageView.setImageDrawable(null)
                    imageView.background = null
                    gridView.visibility = View.GONE
                    numberButtonsContainer.visibility = View.GONE
                    actionButton.visibility = View.GONE

                    // Build image source selection buttons
                    buildImageSourceButtons(numberButtonsContainer)
                    numberButtonsContainer.visibility = View.VISIBLE
                }
                1 -> { // Pick number
                    instructionText.text = "Step 2: Choose your secret number (0-9)"
                    if (selectedImageSource == ImageSource.SYSTEM_WALLPAPER) {
                        val bitmap = WallpaperHelper.getLockScreenBitmap(this)
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                        } else {
                            Toast.makeText(this, "Cannot read system wallpaper", Toast.LENGTH_SHORT).show()
                            advanceToStep(0)
                            return
                        }
                    } else {
                        imageView.setImageURI(selectedImageUri)
                    }
                    imageView.background = null
                    gridView.visibility = View.VISIBLE
                    gridView.alwaysShowDigits = true
                    gridView.numberGrid = NumberGridFactory.createRandomGrid()
                    gridView.highlightedDigit = -1
                    numberButtonsContainer.visibility = View.VISIBLE
                    actionButton.visibility = View.GONE

                    buildNumberButtons(numberButtonsContainer)
                }
                2 -> { // Pick location
                    instructionText.text = "Step 3: Tap your secret spot on the picture\n(This is where you'll drag number $selectedNumber to unlock)"
                    numberButtonsContainer.visibility = View.GONE
                    gridView.visibility = View.VISIBLE
                    gridView.alwaysShowDigits = false
                    gridView.numberGrid = null  // hide numbers during point selection
                    gridView.showTargetPoint = false
                    actionButton.visibility = View.GONE

                    gridView.setOnTouchListener { v, event ->
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                            secretX = event.x / v.width
                            secretY = event.y / v.width  // intentionally /width, not /height
                            secretX = secretX.coerceIn(0.05f, 0.95f)

                            gridView.showTargetPoint = true
                            gridView.targetPointX = secretX
                            gridView.targetPointY = secretY
                            gridView.toleranceRadius = PicturePasswordConfig.DEFAULT_TOLERANCE
                            gridView.invalidate()

                            advanceToStep(3)
                            v.performClick()
                        }
                        true
                    }
                }
                3 -> { // Confirm
                    instructionText.text = "Your secret: Drag number $selectedNumber to the target.\nTap 'Save' to confirm."
                    gridView.setOnTouchListener(null)
                    numberButtonsContainer.visibility = View.GONE
                    gridView.visibility = View.VISIBLE
                    gridView.alwaysShowDigits = true
                    gridView.showTargetPoint = true
                    gridView.targetPointX = secretX
                    gridView.targetPointY = secretY
                    gridView.highlightedDigit = selectedNumber
                    gridView.numberGrid = NumberGridFactory.createRandomGrid()

                    actionButton.text = "Save Picture Password"
                    actionButton.visibility = View.VISIBLE
                    actionButton.setOnClickListener {
                        val hasImage = if (selectedImageSource == ImageSource.SYSTEM_WALLPAPER) {
                            WallpaperHelper.getLockScreenBitmap(this) != null
                        } else {
                            selectedImageUri != null
                        }

                        if (hasImage && selectedNumber in 0..9 && secretX >= 0 && secretY >= 0) {
                            // For system wallpaper, we use a sentinel URI
                            val uri = if (selectedImageSource == ImageSource.SYSTEM_WALLPAPER) {
                                Uri.parse(WALLPAPER_SENTINEL_URI)
                            } else {
                                selectedImageUri!!
                            }

                            val config = PicturePasswordConfig(
                                imageUri = uri,
                                secretNumber = selectedNumber,
                                secretX = secretX,
                                secretY = secretY
                            )
                            passwordStore.save(config)

                            // Save image source and wallpaper hash
                            settingsStore.imageSource = selectedImageSource
                            if (selectedImageSource == ImageSource.SYSTEM_WALLPAPER) {
                                settingsStore.wallpaperHash = WallpaperHelper.computeWallpaperHash(this)
                                settingsStore.wallpaperChanged = false
                            }

                            Toast.makeText(this, "Picture Password saved!", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this, "Setup incomplete", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Setup error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Build image source selection buttons: "Choose from gallery" and "Use system wallpaper"
     */
    private fun buildImageSourceButtons(container: LinearLayout) {
        container.removeAllViews()

        val label = TextView(this).apply {
            text = "Choose image source:"
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(12))
        }
        container.addView(label)

        // Gallery button
        val galleryBtn = createSourceButton("🖼️  Choose from gallery") {
            imagePickerLauncher.launch(arrayOf("image/*"))
        }
        container.addView(galleryBtn)

        // System wallpaper button — always enabled; will request permission if needed
        val wallpaperBtn = createSourceButton("📱  Use system wallpaper") {
            tryUseSystemWallpaper()
        }
        container.addView(wallpaperBtn)

        // Show hint if wallpaper is not currently readable (but button stays tappable)
        if (!hasWallpaperPermission()) {
            val hint = TextView(this).apply {
                text = "ℹ️ Media permission will be requested when tapped"
                setTextColor(ContextCompat.getColor(context, R.color.on_surface_hint))
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(6), 0, 0)
            }
            container.addView(hint)
        } else {
            val wallpaperBitmap = WallpaperHelper.getLockScreenBitmap(this)
            if (wallpaperBitmap == null) {
                val hint = TextView(this).apply {
                    text = "⚠️ System wallpaper not available (may be a live wallpaper)"
                    setTextColor(ContextCompat.getColor(context, R.color.warning_title))
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setPadding(0, dpToPx(6), 0, 0)
                }
                container.addView(hint)
            }
        }
    }

    private fun createSourceButton(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            }

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(ContextCompat.getColor(context, R.color.button_secondary_bg))
                setStroke(dpToPx(1), ContextCompat.getColor(context, R.color.button_secondary_stroke))
            }

            setOnClickListener { onClick() }
        }
    }

    /**
     * Build high-contrast number buttons (0-9) programmatically.
     * White text on dark rounded background with clear spacing.
     */
    private fun buildNumberButtons(container: LinearLayout) {
        container.removeAllViews()

        val label = TextView(this).apply {
            text = "Tap your secret number:"
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(8))
        }
        container.addView(label)

        // Row 1: digits 0-4
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        for (i in 0..4) row1.addView(createNumberButton(i))
        container.addView(row1)

        // Row 2: digits 5-9
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(6) }
        }
        for (i in 5..9) row2.addView(createNumberButton(i))
        container.addView(row2)
    }

    private fun createNumberButton(digit: Int): TextView {
        val size = dpToPx(52)
        val margin = dpToPx(5)

        return TextView(this).apply {
            text = digit.toString()
            textSize = 24f
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(margin, margin, margin, margin)
            }

            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            paint.strokeWidth = 3f

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(ContextCompat.getColor(context, R.color.button_secondary_bg))
                setStroke(dpToPx(1), ContextCompat.getColor(context, R.color.button_secondary_stroke))
            }

            setOnClickListener {
                selectedNumber = digit

                (background as? GradientDrawable)?.apply {
                    setColor(ContextCompat.getColor(context, R.color.primary))
                    setStroke(dpToPx(2), ContextCompat.getColor(context, R.color.radio_tint))
                }

                this.post {
                    val gridView = this@SetupActivity.findViewById<NumberGridView>(R.id.setupGridView)
                    gridView?.highlightedDigit = digit
                    advanceToStep(2)
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        /** Sentinel URI stored in PasswordStore when using system wallpaper */
        const val WALLPAPER_SENTINEL_URI = "picturepassword://system-wallpaper"
    }

    // ---- Wallpaper permission helpers ----

    /** The runtime permission needed to read the wallpaper on this API level */
    private fun wallpaperPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    /** Whether the wallpaper-read permission is already granted */
    private fun hasWallpaperPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, wallpaperPermission()) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Attempt to use the system wallpaper.
     * If the bitmap is readable, proceed to step 1.
     * If not and permission is missing, request it.
     * Otherwise show an error (live wallpaper, etc.).
     */
    private fun tryUseSystemWallpaper() {
        val bitmap = WallpaperHelper.getLockScreenBitmap(this)
        if (bitmap != null) {
            selectedImageSource = ImageSource.SYSTEM_WALLPAPER
            selectedImageUri = null
            advanceToStep(1)
        } else if (!hasWallpaperPermission()) {
            wallpaperPermissionLauncher.launch(wallpaperPermission())
        } else {
            Toast.makeText(
                this,
                "Cannot read system wallpaper.\nIt may be a live wallpaper or otherwise inaccessible.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
