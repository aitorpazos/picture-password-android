package com.aitorpazos.picturepassword.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
        val numberButtonsContainer = findViewById<View>(R.id.numberButtonsContainer)

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

                // Setup number buttons
                for (i in 0..9) {
                    val btnId = resources.getIdentifier("numBtn$i", "id", packageName)
                    if (btnId != 0) {
                        findViewById<Button>(btnId)?.setOnClickListener {
                            selectedNumber = i
                            gridView.highlightedDigit = i
                            advanceToStep(2)
                        }
                    }
                }
            }
            2 -> { // Pick location
                instructionText.text = "Step 3: Tap your secret spot on the picture\n(This is where you'll drag number $selectedNumber to unlock)"
                numberButtonsContainer.visibility = View.GONE
                gridView.visibility = View.GONE
                actionButton.visibility = View.GONE

                imageView.setOnClickListener { event ->
                    // Not ideal — use a proper touch listener
                }
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
}
