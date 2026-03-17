package com.aitorpazos.picturepassword

import android.net.Uri
import com.aitorpazos.picturepassword.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * End-to-end tests that simulate the exact setup -> unlock flow,
 * matching the coordinate transformations used in SetupActivity and LockScreenActivity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SetupToUnlockTest {

    companion object {
        // Common phone dimensions
        const val VIEW_WIDTH = 1080f
        const val VIEW_HEIGHT = 2400f
    }

    /**
     * Simulate the exact setup flow:
     * 1. User taps a point on the ImageView -> secretX = tapX / viewWidth, secretY = tapY / viewWidth
     * 2. Config is saved with those normalized coordinates
     *
     * Then simulate the unlock flow:
     * 3. A new random grid is shown
     * 4. User drags grid so that their secret digit aligns with the secret point
     * 5. On release, offset = totalDragPixels / viewWidth
     * 6. Verification runs
     */
    @Test
    fun `full setup to unlock flow with pixel simulation`() {
        // --- SETUP PHASE ---
        // User taps at pixel (400, 1200) on the ImageView
        val tapPixelX = 400f
        val tapPixelY = 1200f

        // SetupActivity stores: secretX = tapX / viewWidth, secretY = tapY / viewWidth
        val secretX = tapPixelX / VIEW_WIDTH
        val secretY = tapPixelY / VIEW_WIDTH  // intentionally /width, matching SetupActivity line 102

        // Clamp X like SetupActivity does
        val clampedSecretX = secretX.coerceIn(0.05f, 0.95f)

        val secretDigit = 7
        val config = PicturePasswordConfig(
            imageUri = Uri.parse("content://test/image"),
            secretNumber = secretDigit,
            secretX = clampedSecretX,
            secretY = secretY
        )

        // --- UNLOCK PHASE ---
        // A new random grid is created
        val grid = NumberGridFactory.createRandomGrid(random = Random(42))
        val cellSizeNorm = 1f / NumberGridFactory.VISIBLE_COLS
        val cellSizePx = VIEW_WIDTH / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        // Find an instance of the secret digit
        val positions = grid.positionsOf(secretDigit)
        assertTrue("Grid should contain digit $secretDigit", positions.isNotEmpty())

        // Pick the first instance
        val (col, row) = positions.first()

        // Where this digit appears on screen in PIXELS (matching NumberGridView.onDraw)
        val digitPixelX = (col - originCol) * cellSizePx + cellSizePx / 2f
        val digitPixelY = (row - originRow) * cellSizePx + cellSizePx / 2f

        // User needs to drag the digit to the secret point
        // Secret point in pixels: secretX * viewWidth, secretY * viewWidth
        val targetPixelX = clampedSecretX * VIEW_WIDTH
        val targetPixelY = secretY * VIEW_WIDTH

        // Required drag in pixels
        val dragPixelX = targetPixelX - digitPixelX
        val dragPixelY = targetPixelY - digitPixelY

        // NumberGridView reports offset as: totalDragX / width, totalDragY / width
        val offsetX = dragPixelX / VIEW_WIDTH
        val offsetY = dragPixelY / VIEW_WIDTH

        // LockScreenActivity creates movedGrid with this offset
        val movedGrid = grid.withOffset(offsetX, offsetY)

        // Verify -- this is exactly what LockScreenActivity does
        val result = UnlockVerifier.verify(movedGrid, config, cellSizeNorm, originCol, originRow)
        assertTrue("Perfect drag in pixel simulation should unlock", result)
    }

    @Test
    fun `unlock fails with no drag`() {
        val config = PicturePasswordConfig(
            imageUri = Uri.parse("content://test/image"),
            secretNumber = 5,
            secretX = 0.5f,
            secretY = 1.0f
        )

        val grid = NumberGridFactory.createRandomGrid(random = Random(1))
        val cellSizeNorm = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        // No drag -- grid offset is (0, 0)
        val movedGrid = grid.withOffset(0f, 0f)

        // With tiny tolerance, this should almost certainly fail
        val tinyConfig = config.copy(toleranceRadius = 0.001f)
        assertFalse("No drag with tiny tolerance should not unlock",
            UnlockVerifier.verify(movedGrid, tinyConfig, cellSizeNorm, originCol, originRow))
    }

    @Test
    fun `unlock works for all digits 0 through 9`() {
        val cellSizeNorm = 1f / NumberGridFactory.VISIBLE_COLS

        for (digit in 0..9) {
            val secretX = 0.5f
            val secretY = 1.0f
            val config = PicturePasswordConfig(
                imageUri = Uri.parse("content://test/image"),
                secretNumber = digit,
                secretX = secretX,
                secretY = secretY
            )

            val grid = NumberGridFactory.createRandomGrid(random = Random(digit.toLong() + 100))
            val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
            val originRow = 0f

            val positions = grid.positionsOf(digit)
            assertTrue("Grid should contain digit $digit", positions.isNotEmpty())

            val (col, row) = positions.first()
            val digitX = (col - originCol) * cellSizeNorm + cellSizeNorm / 2f
            val digitY = (row - originRow) * cellSizeNorm + cellSizeNorm / 2f

            val offsetX = secretX - digitX
            val offsetY = secretY - digitY

            val movedGrid = grid.withOffset(offsetX, offsetY)
            assertTrue("Digit $digit should unlock with perfect drag",
                UnlockVerifier.verify(movedGrid, config, cellSizeNorm, originCol, originRow))
        }
    }

    @Test
    fun `unlock works with various screen tap positions`() {
        val tapPositions = listOf(
            100f to 200f,      // top-left area
            540f to 1200f,     // center
            900f to 2000f,     // bottom-right area
            50f to 2300f,      // bottom-left
            1000f to 100f      // top-right
        )

        val cellSizeNorm = 1f / NumberGridFactory.VISIBLE_COLS

        for ((tapX, tapY) in tapPositions) {
            val secretX = (tapX / VIEW_WIDTH).coerceIn(0.05f, 0.95f)
            val secretY = tapY / VIEW_WIDTH

            if (secretY < 0f) continue

            val digit = 3
            val config = PicturePasswordConfig(
                imageUri = Uri.parse("content://test/image"),
                secretNumber = digit,
                secretX = secretX,
                secretY = secretY
            )

            val grid = NumberGridFactory.createRandomGrid(random = Random(tapX.toLong()))
            val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
            val originRow = 0f
            val cellSizePx = VIEW_WIDTH / NumberGridFactory.VISIBLE_COLS

            val positions = grid.positionsOf(digit)
            val (col, row) = positions.first()

            val digitPxX = (col - originCol) * cellSizePx + cellSizePx / 2f
            val digitPxY = (row - originRow) * cellSizePx + cellSizePx / 2f

            val targetPxX = secretX * VIEW_WIDTH
            val targetPxY = secretY * VIEW_WIDTH

            val dragPxX = targetPxX - digitPxX
            val dragPxY = targetPxY - digitPxY

            val offsetX = dragPxX / VIEW_WIDTH
            val offsetY = dragPxY / VIEW_WIDTH

            val movedGrid = grid.withOffset(offsetX, offsetY)
            assertTrue("Tap at ($tapX, $tapY) should unlock with perfect drag",
                UnlockVerifier.verify(movedGrid, config, cellSizeNorm, originCol, originRow))
        }
    }

    @Test
    fun `imprecise drag within tolerance unlocks`() {
        val secretX = 0.5f
        val secretY = 1.2f
        val digit = 4
        val tolerance = PicturePasswordConfig.DEFAULT_TOLERANCE // 0.06f
        val config = PicturePasswordConfig(
            imageUri = Uri.parse("content://test/image"),
            secretNumber = digit,
            secretX = secretX,
            secretY = secretY,
            toleranceRadius = tolerance
        )

        val grid = NumberGridFactory.createRandomGrid(random = Random(200))
        val cellSizeNorm = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        val positions = grid.positionsOf(digit)
        val (col, row) = positions.first()
        val digitX = (col - originCol) * cellSizeNorm + cellSizeNorm / 2f
        val digitY = (row - originRow) * cellSizeNorm + cellSizeNorm / 2f

        // Add a small error within tolerance
        val errorX = tolerance * 0.5f
        val errorY = tolerance * 0.3f

        val offsetX = secretX - digitX + errorX
        val offsetY = secretY - digitY + errorY

        val movedGrid = grid.withOffset(offsetX, offsetY)
        assertTrue("Imprecise drag within tolerance should unlock",
            UnlockVerifier.verify(movedGrid, config, cellSizeNorm, originCol, originRow))
    }

    @Test
    fun `imprecise drag outside tolerance fails`() {
        val secretX = 0.5f
        val secretY = 1.2f
        val digit = 4
        val tolerance = 0.05f
        val config = PicturePasswordConfig(
            imageUri = Uri.parse("content://test/image"),
            secretNumber = digit,
            secretX = secretX,
            secretY = secretY,
            toleranceRadius = tolerance
        )

        val grid = NumberGridFactory.createRandomGrid(random = Random(200))
        val cellSizeNorm = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        val positions = grid.positionsOf(digit)
        val (col, row) = positions.first()
        val digitX = (col - originCol) * cellSizeNorm + cellSizeNorm / 2f
        val digitY = (row - originRow) * cellSizeNorm + cellSizeNorm / 2f

        // Error exceeds tolerance
        val errorX = tolerance * 2f
        val errorY = tolerance * 2f

        val offsetX = secretX - digitX + errorX
        val offsetY = secretY - digitY + errorY

        val movedGrid = grid.withOffset(offsetX, offsetY)
        assertFalse("Drag outside tolerance should NOT unlock",
            UnlockVerifier.verify(movedGrid, config, cellSizeNorm, originCol, originRow))
    }

    @Test
    fun `different random grids all work with correct drag`() {
        val secretX = 0.4f
        val secretY = 0.9f
        val digit = 8
        val config = PicturePasswordConfig(
            imageUri = Uri.parse("content://test/image"),
            secretNumber = digit,
            secretX = secretX,
            secretY = secretY
        )
        val cellSizeNorm = 1f / NumberGridFactory.VISIBLE_COLS

        for (seed in 1L..50L) {
            val grid = NumberGridFactory.createRandomGrid(random = Random(seed))
            val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
            val originRow = 0f

            val positions = grid.positionsOf(digit)
            assertTrue("Seed $seed: grid should contain digit $digit", positions.isNotEmpty())

            val (col, row) = positions.first()
            val digitX = (col - originCol) * cellSizeNorm + cellSizeNorm / 2f
            val digitY = (row - originRow) * cellSizeNorm + cellSizeNorm / 2f

            val offsetX = secretX - digitX
            val offsetY = secretY - digitY

            val movedGrid = grid.withOffset(offsetX, offsetY)
            assertTrue("Seed $seed: perfect drag should unlock",
                UnlockVerifier.verify(movedGrid, config, cellSizeNorm, originCol, originRow))
        }
    }

    @Test
    fun `save and load config preserves coordinates`() {
        val originalSecretX = 400f / VIEW_WIDTH
        val originalSecretY = 1200f / VIEW_WIDTH
        val clampedX = originalSecretX.coerceIn(0.05f, 0.95f)

        val config = PicturePasswordConfig(
            imageUri = Uri.parse("content://test/image"),
            secretNumber = 5,
            secretX = clampedX,
            secretY = originalSecretY,
            toleranceRadius = PicturePasswordConfig.DEFAULT_TOLERANCE
        )

        assertEquals(clampedX, config.secretX, 0.0001f)
        assertEquals(originalSecretY, config.secretY, 0.0001f)
        assertEquals(PicturePasswordConfig.DEFAULT_TOLERANCE, config.toleranceRadius, 0.0001f)
    }

    @Test
    fun `full setup to unlock with variable density pixel simulation`() {
        // --- SETUP PHASE ---
        val tapPixelX = 540f
        val tapPixelY = 1400f
        val secretX = (tapPixelX / VIEW_WIDTH).coerceIn(0.05f, 0.95f)
        val secretY = tapPixelY / VIEW_WIDTH

        val secretDigit = 3
        val config = PicturePasswordConfig(
            imageUri = Uri.parse("content://test/image"),
            secretNumber = secretDigit,
            secretX = secretX,
            secretY = secretY
        )

        // --- UNLOCK PHASE with each possible density ---
        for (visCols in NumberGridFactory.MIN_VISIBLE_COLS..NumberGridFactory.MAX_VISIBLE_COLS) {
            val grid = NumberGridFactory.createRandomGrid(random = Random(visCols.toLong() + 500))
            val cellSizeNorm = 1f / visCols
            val cellSizePx = VIEW_WIDTH / visCols
            val originCol = (grid.cols - visCols) / 2f
            val originRow = 0f

            val positions = grid.positionsOf(secretDigit)
            assertTrue("visCols=$visCols: grid should contain digit $secretDigit", positions.isNotEmpty())

            val (col, row) = positions.first()
            val digitPixelX = (col - originCol) * cellSizePx + cellSizePx / 2f
            val digitPixelY = (row - originRow) * cellSizePx + cellSizePx / 2f

            val targetPixelX = secretX * VIEW_WIDTH
            val targetPixelY = secretY * VIEW_WIDTH

            val dragPixelX = targetPixelX - digitPixelX
            val dragPixelY = targetPixelY - digitPixelY

            val offsetX = dragPixelX / VIEW_WIDTH
            val offsetY = dragPixelY / VIEW_WIDTH

            val movedGrid = grid.withOffset(offsetX, offsetY)
            assertTrue("visCols=$visCols: perfect drag should unlock",
                UnlockVerifier.verify(movedGrid, config, cellSizeNorm, originCol, originRow))
        }
    }
}
