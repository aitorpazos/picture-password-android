package com.aitorpazos.picturepassword

import android.net.Uri
import com.aitorpazos.picturepassword.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UnlockFlowTest {

    companion object {
        const val SCREEN_WIDTH = 1080f
        const val SCREEN_HEIGHT = 2400f
    }

    private fun makeConfig(number: Int, x: Float, y: Float, tolerance: Float = PicturePasswordConfig.DEFAULT_TOLERANCE): PicturePasswordConfig {
        return PicturePasswordConfig(
            imageUri = Uri.parse("content://test/image"),
            secretNumber = number,
            secretX = x,
            secretY = y,
            toleranceRadius = tolerance
        )
    }

    @Test
    fun `perfect drag to align digit with secret point unlocks`() {
        val secretDigit = 5
        val secretX = 540f / SCREEN_WIDTH
        val secretY = 1200f / SCREEN_WIDTH
        val config = makeConfig(secretDigit, secretX, secretY)

        val grid = NumberGridFactory.createRandomGrid(random = Random(123))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        val positions = grid.positionsOf(secretDigit)
        assertTrue("Grid should contain digit $secretDigit", positions.isNotEmpty())

        val (col, row) = positions.first()
        val digitScreenX = (col - originCol) * cellSize + cellSize / 2f
        val digitScreenY = (row - originRow) * cellSize + cellSize / 2f

        val dragOffsetX = secretX - digitScreenX
        val dragOffsetY = secretY - digitScreenY

        val movedGrid = grid.withOffset(dragOffsetX, dragOffsetY)
        assertTrue("Perfect drag should unlock", UnlockVerifier.verify(movedGrid, config, cellSize, originCol, originRow))
    }

    @Test
    fun `imprecise drag within tolerance unlocks`() {
        val secretDigit = 3
        val secretX = 0.4f
        val secretY = 0.8f
        val tolerance = 0.06f
        val config = makeConfig(secretDigit, secretX, secretY, tolerance)

        val grid = NumberGridFactory.createRandomGrid(random = Random(77))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        val positions = grid.positionsOf(secretDigit)
        val (col, row) = positions.first()
        val digitScreenX = (col - originCol) * cellSize + cellSize / 2f
        val digitScreenY = (row - originRow) * cellSize + cellSize / 2f

        val error = tolerance * 0.5f
        val dragOffsetX = secretX - digitScreenX + error * 0.7f
        val dragOffsetY = secretY - digitScreenY + error * 0.7f

        val movedGrid = grid.withOffset(dragOffsetX, dragOffsetY)
        assertTrue("Slightly imprecise drag within tolerance should unlock", UnlockVerifier.verify(movedGrid, config, cellSize, originCol, originRow))
    }

    @Test
    fun `drag beyond tolerance does not unlock`() {
        val secretDigit = 7
        val secretX = 0.5f
        val secretY = 1.0f
        val tolerance = 0.06f

        val grid = NumberGridFactory.createRandomGrid(random = Random(99))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        val tinyConfig = makeConfig(secretDigit, secretX, secretY, 0.001f)

        val positions = grid.positionsOf(secretDigit)
        val (col, row) = positions.first()
        val digitScreenX = (col - originCol) * cellSize + cellSize / 2f
        val digitScreenY = (row - originRow) * cellSize + cellSize / 2f

        val dragOffsetX = secretX - digitScreenX + tolerance * 3f
        val dragOffsetY = secretY - digitScreenY + tolerance * 3f

        val movedGrid = grid.withOffset(dragOffsetX, dragOffsetY)
        assertFalse("Drag beyond tolerance should NOT unlock", UnlockVerifier.verify(movedGrid, tinyConfig, cellSize, originCol, originRow))
    }

    @Test
    fun `wrong digit at correct position does not unlock`() {
        val secretDigit = 4
        val wrongDigit = 8
        val secretX = 0.5f
        val secretY = 1.0f
        val config = makeConfig(secretDigit, secretX, secretY)

        val grid = NumberGridFactory.createRandomGrid(random = Random(42))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        val positions = grid.positionsOf(wrongDigit)
        val (col, row) = positions.first()
        val digitScreenX = (col - originCol) * cellSize + cellSize / 2f
        val digitScreenY = (row - originRow) * cellSize + cellSize / 2f

        val dragOffsetX = secretX - digitScreenX
        val dragOffsetY = secretY - digitScreenY

        val movedGrid = grid.withOffset(dragOffsetX, dragOffsetY)
        assertFalse("Wrong digit at correct position should NOT unlock", UnlockVerifier.verify(movedGrid, config, cellSize, originCol, originRow))
    }

    @Test
    fun `unlock works across 20 different random grids`() {
        val secretDigit = 6
        val secretX = 0.3f
        val secretY = 1.5f
        val config = makeConfig(secretDigit, secretX, secretY)
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS

        for (seed in 1..20) {
            val grid = NumberGridFactory.createRandomGrid(random = Random(seed.toLong()))
            val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
            val originRow = 0f

            val positions = grid.positionsOf(secretDigit)
            assertTrue("Seed $seed: grid should contain digit $secretDigit", positions.isNotEmpty())

            val (col, row) = positions.first()
            val digitScreenX = (col - originCol) * cellSize + cellSize / 2f
            val digitScreenY = (row - originRow) * cellSize + cellSize / 2f

            val dragOffsetX = secretX - digitScreenX
            val dragOffsetY = secretY - digitScreenY

            val movedGrid = grid.withOffset(dragOffsetX, dragOffsetY)
            assertTrue("Seed $seed: perfect drag should unlock", UnlockVerifier.verify(movedGrid, config, cellSize, originCol, originRow))
        }
    }

    @Test
    fun `unlock works with variable grid density (different visibleCols)`() {
        val secretDigit = 5
        val secretX = 0.45f
        val secretY = 1.0f
        val config = makeConfig(secretDigit, secretX, secretY)

        // Test each possible visible cols value
        for (visCols in NumberGridFactory.MIN_VISIBLE_COLS..NumberGridFactory.MAX_VISIBLE_COLS) {
            val cellSize = 1f / visCols

            for (seed in 1..10) {
                val grid = NumberGridFactory.createRandomGrid(random = Random(seed.toLong() + visCols * 100))
                val originCol = (grid.cols - visCols) / 2f
                val originRow = 0f

                val positions = grid.positionsOf(secretDigit)
                assertTrue("visCols=$visCols seed=$seed: grid should contain digit $secretDigit", positions.isNotEmpty())

                val (col, row) = positions.first()
                val digitScreenX = (col - originCol) * cellSize + cellSize / 2f
                val digitScreenY = (row - originRow) * cellSize + cellSize / 2f

                val dragOffsetX = secretX - digitScreenX
                val dragOffsetY = secretY - digitScreenY

                val movedGrid = grid.withOffset(dragOffsetX, dragOffsetY)
                assertTrue("visCols=$visCols seed=$seed: perfect drag should unlock",
                    UnlockVerifier.verify(movedGrid, config, cellSize, originCol, originRow))
            }
        }
    }

    @Test
    fun `variable density does not affect wrong digit rejection`() {
        val secretDigit = 4
        val wrongDigit = 8
        val secretX = 0.5f
        val secretY = 1.0f
        val config = makeConfig(secretDigit, secretX, secretY)

        for (visCols in NumberGridFactory.MIN_VISIBLE_COLS..NumberGridFactory.MAX_VISIBLE_COLS) {
            val cellSize = 1f / visCols
            val grid = NumberGridFactory.createRandomGrid(random = Random(visCols.toLong()))
            val originCol = (grid.cols - visCols) / 2f
            val originRow = 0f

            // Align wrong digit with target
            val positions = grid.positionsOf(wrongDigit)
            val (col, row) = positions.first()
            val digitScreenX = (col - originCol) * cellSize + cellSize / 2f
            val digitScreenY = (row - originRow) * cellSize + cellSize / 2f

            val dragOffsetX = secretX - digitScreenX
            val dragOffsetY = secretY - digitScreenY

            val movedGrid = grid.withOffset(dragOffsetX, dragOffsetY)
            assertFalse("visCols=$visCols: wrong digit should NOT unlock",
                UnlockVerifier.verify(movedGrid, config, cellSize, originCol, originRow))
        }
    }

    @Test
    fun `pixel-level simulation matches verification`() {
        val viewWidth = SCREEN_WIDTH

        val tapPixelX = 400f
        val tapPixelY = 1000f
        val secretX = tapPixelX / viewWidth
        val secretY = tapPixelY / viewWidth

        val secretDigit = 2
        val config = makeConfig(secretDigit, secretX, secretY)

        val grid = NumberGridFactory.createRandomGrid(random = Random(555))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val cellPx = viewWidth / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        val positions = grid.positionsOf(secretDigit)
        val (col, row) = positions.first()

        val digitPixelX = (col - originCol) * cellPx + cellPx / 2f
        val digitPixelY = (row - originRow) * cellPx + cellPx / 2f

        val dragPixelX = tapPixelX - digitPixelX
        val dragPixelY = tapPixelY - digitPixelY

        val offsetX = dragPixelX / viewWidth
        val offsetY = dragPixelY / viewWidth

        val movedGrid = grid.withOffset(offsetX, offsetY)
        assertTrue("Pixel-level simulation should unlock", UnlockVerifier.verify(movedGrid, config, cellSize, originCol, originRow))
    }

    @Test
    fun `tall screen secretY greater than 1 works`() {
        val secretDigit = 9
        val secretX = 0.5f
        val secretY = 2200f / SCREEN_WIDTH
        val config = makeConfig(secretDigit, secretX, secretY)

        val grid = NumberGridFactory.createRandomGrid(random = Random(333))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        val positions = grid.positionsOf(secretDigit)
        val (col, row) = positions.first()
        val digitScreenX = (col - originCol) * cellSize + cellSize / 2f
        val digitScreenY = (row - originRow) * cellSize + cellSize / 2f

        val dragOffsetX = secretX - digitScreenX
        val dragOffsetY = secretY - digitScreenY

        val movedGrid = grid.withOffset(dragOffsetX, dragOffsetY)
        assertTrue("Tall screen (secretY > 1.0) should unlock with correct drag", UnlockVerifier.verify(movedGrid, config, cellSize, originCol, originRow))
    }

    @Test
    fun `grid covers full tall screen`() {
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val gridHeightNorm = NumberGridFactory.DEFAULT_ROWS * cellSize
        val tallAspectRatio = SCREEN_HEIGHT / SCREEN_WIDTH

        assertTrue("Grid height ($gridHeightNorm) should exceed screen aspect ratio ($tallAspectRatio)", gridHeightNorm > tallAspectRatio)
    }
}
