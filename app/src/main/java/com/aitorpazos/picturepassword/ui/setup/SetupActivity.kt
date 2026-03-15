package com.aitorpazos.picturepassword.ui.setup

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.aitorpazos.picturepassword.R
import com.aitorpazos.picturepassword.crypto.PasswordStore
import com.aitorpazos.picturepassword.model.NumberGridFactory
import com.aitorpazos.picturepassword.model.PicturePasswordConfig
import com.aitorpazos.picturepassword.ui.views.NumberGridView

class SetupActivity : AppCompatActivity() {

    private lateinit var passwordStore: PasswordStore

    private var selectedImageUri: Uri? = null
    private var selectedNumber: Int = -1
    private var secretX: Float = -1f
    private var secretY: Float = -1f
    private var setupStep = 0 // 0=pick image, 1=pick number, 2=pick location, 3=confirm

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
                0 -> { // Pick image
                    instructionText.text = "Step 1: Choose a picture for your lock screen"
                    imageView.setImageDrawable(null)
                    imageView.background = null
                    gridView.visibility = View.GONE
                    numberButtonsContainer.visibility = View.GONE
                    actionButton.text = "Choose Picture"
                    actionButton.visibility = View.VISIBLE
                    actionButton.setOnClickListener {
                        imagePickerLauncher.launch(arrayOf("image/*"))
                    }
                }
                1 -> { // Pick number
                    instructionText.text = "Step 2: Choose your secret number (0-9)"
                    imageView.setImageURI(selectedImageUri)
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
                        val uri = selectedImageUri
                        if (uri != null && selectedNumber in 0..9 && secretX >= 0 && secretY >= 0) {
                            val config = PicturePasswordConfig(
                                imageUri = uri,
                                secretNumber = selectedNumber,
                                secretX = secretX,
                                secretY = secretY
                            )
                            passwordStore.save(config)
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
     * Build high-contrast number buttons (0-9) programmatically.
     * White text on dark rounded background with clear spacing.
     */
    private fun buildNumberButtons(container: LinearLayout) {
        container.removeAllViews()

        val label = TextView(this).apply {
            text = "Tap your secret number:"
            setTextColor(Color.WHITE)
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
            setTextColor(Color.WHITE)
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
                setColor(Color.argb(120, 0, 0, 0))
                setStroke(dpToPx(1), Color.argb(180, 255, 255, 255))
            }

            setOnClickListener {
                selectedNumber = digit

                (background as? GradientDrawable)?.apply {
                    setColor(Color.argb(200, 25, 118, 210))
                    setStroke(dpToPx(2), Color.argb(255, 100, 180, 255))
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
}
