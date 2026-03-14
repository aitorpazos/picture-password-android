package com.aitorpazos.picturepassword.ui.setup

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
        setContentView(R.layout.activity_setup)

        passwordStore = PasswordStore(this)

        advanceToStep(0)
    }

    private fun advanceToStep(step: Int) {
        setupStep = step
        val instructionText = findViewById<TextView>(R.id.setupInstructionText)
        val imageView = findViewById<ImageView>(R.id.setupImageView)
        val gridView = findViewById<NumberGridView>(R.id.setupGridView)
        val actionButton = findViewById<Button>(R.id.setupActionButton)
        val numberButtonsContainer = findViewById<LinearLayout>(R.id.numberButtonsContainer)

        when (step) {
            0 -> { // Pick image
                instructionText.text = "Step 1: Choose a picture for your lock screen"
                imageView.setImageResource(android.R.color.darker_gray)
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
                gridView.visibility = View.VISIBLE
                gridView.numberGrid = NumberGridFactory.createRandomGrid()
                numberButtonsContainer.visibility = View.VISIBLE
                actionButton.visibility = View.GONE

                // Build high-contrast number buttons programmatically
                buildNumberButtons(numberButtonsContainer)
            }
            2 -> { // Pick location
                instructionText.text = "Step 3: Tap your secret spot on the picture\n(This is where you'll drag number $selectedNumber to unlock)"
                numberButtonsContainer.visibility = View.GONE
                gridView.visibility = View.GONE
                actionButton.visibility = View.GONE

                imageView.setOnTouchListener { v, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        secretX = event.x / v.width
                        secretY = event.y / v.height
                        secretX = secretX.coerceIn(0.05f, 0.95f)
                        secretY = secretY.coerceIn(0.05f, 0.95f)

                        gridView.visibility = View.VISIBLE
                        gridView.showTargetPoint = true
                        gridView.targetPointX = secretX
                        gridView.targetPointY = secretY
                        gridView.invalidate()

                        advanceToStep(3)
                        v.performClick()
                    }
                    true
                }
            }
            3 -> { // Confirm
                instructionText.text = "Your secret: Drag number $selectedNumber to the red target.\nTap 'Save' to confirm."
                imageView.setOnTouchListener(null)
                numberButtonsContainer.visibility = View.GONE
                gridView.visibility = View.VISIBLE
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
    }

    /**
     * Build high-contrast number buttons (0-9) programmatically.
     * White text on dark rounded background with clear spacing.
     */
    private fun buildNumberButtons(container: LinearLayout) {
        container.removeAllViews()

        // Label
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
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(margin, margin, margin, margin)
            }

            // Rounded dark background with white border
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(220, 30, 30, 30))
                setStroke(dpToPx(2), Color.WHITE)
            }

            // Elevation for depth
            elevation = dpToPx(4).toFloat()

            setOnClickListener {
                selectedNumber = digit
                val gridView = findViewById<NumberGridView>(R.id.setupGridView)
                gridView.highlightedDigit = digit
                advanceToStep(2)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
