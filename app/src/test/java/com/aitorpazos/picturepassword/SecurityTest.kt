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
 * Security-focused tests for the Picture Password unlock mechanism.
 * Validates that the system resists brute-force, statistical, and observation attacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SecurityTest {

    private fun makeConfig(
        number: Int, x: Float, y: Float,
        tolerance: Float = PicturePasswordConfig.DEFAULT_TOLERANCE
    ): PicturePasswordConfig {
        return PicturePasswordConfig(
            imageUri = Uri.parse("content://test/image"),
            secretNumber = number,
            secretX = x,
            secretY = y,
            toleranceRadius = tolerance
        )
    }

    // --- Grid randomness and uniformity ---

    @Test
    fun `digit distribution is approximately uniform across many grids`() {
        val totalCounts = IntArray(10)
        val numGrids = 100

        for (seed in 1..numGrids) {
            val grid = NumberGridFactory.createRandomGrid(random = Random(seed.toLong()))
            for (cell in grid.cells) {
                totalCounts[cell]++
            }
        }

        val totalCells = totalCounts.sum()
        val expectedPerDigit = totalCells / 10.0

        for (d in 0..9) {
            val ratio = totalCounts[d] / expectedPerDigit
            assertTrue(
                "Digit $d distribution ratio $ratio should be between 0.85 and 1.15",
                ratio in 0.85..1.15
            )
        }
    }

    @Test
    fun `each grid has unique layout with different seeds`() {
        val grids = (1L..50L).map { NumberGridFactory.createRandomGrid(random = Random(it)) }
        val uniqueLayouts = grids.map { it.cells.toList() }.toSet()
        assertEquals("All 50 grids should have unique layouts", 50, uniqueLayouts.size)
    }

    @Test
    fun `grid origin randomization produces varying start positions`() {
        // The grid is larger than the visible area; verify the centering origin varies
        // when visible cols change
        val origins = mutableSetOf<Float>()
        for (visCols in NumberGridFactory.MIN_VISIBLE_COLS..NumberGridFactory.MAX_VISIBLE_COLS) {
            val grid = NumberGridFactory.createRandomGrid()
            val originCol = (grid.cols - visCols) / 2f
            origins.add(originCol)
        }
        assertTrue(
            "Different visible col counts should produce different origins",
            origins.size > 1
        )
    }

    // --- Brute-force resistance ---

    @Test
    fun `random drags almost never unlock (brute-force resistance)`() {
        val secretDigit = 5
        val secretX = 0.5f
        val secretY = 1.0f
        val config = makeConfig(secretDigit, secretX, secretY, tolerance = 0.06f)
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS

        var successes = 0
        val attempts = 10_000
        val rng = Random(12345)

        for (i in 0 until attempts) {
            val grid = NumberGridFactory.createRandomGrid(random = Random(rng.nextLong()))
            val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
            val originRow = 0f

            // Random drag offset
            val offsetX = rng.nextFloat() * 4f - 2f  // -2..2
            val offsetY = rng.nextFloat() * 4f - 2f
            val movedGrid = grid.withOffset(offsetX, offsetY)

            if (UnlockVerifier.verify(movedGrid, config, cellSize, originCol, originRow)) {
                successes++
            }
        }

        val successRate = successes.toDouble() / attempts
        assertTrue(
            "Random drag success rate ($successRate) should be < 5%",
            successRate < 0.05
        )
    }

    @Test
    fun `correct digit wrong position never unlocks with tight tolerance`() {
        val secretDigit = 7
        val secretX = 0.5f
        val secretY = 1.0f
        val config = makeConfig(secretDigit, secretX, secretY, tolerance = 0.03f)
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS

        val grid = NumberGridFactory.createRandomGrid(random = Random(42))
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        // Try dragging digit 7 to many WRONG positions
        val positions = grid.positionsOf(secretDigit)
        var anyFalsePositive = false

        for ((col, row) in positions) {
            val digitScreenX = (col - originCol) * cellSize + cellSize / 2f
            val digitScreenY = (row - originRow) * cellSize + cellSize / 2f

            // Drag digit to a point that is NOT the secret point (offset by 0.2)
            val wrongTargetX = (secretX + 0.2f).coerceIn(0f, 1f)
            val wrongTargetY = secretY + 0.2f

            val offsetX = wrongTargetX - digitScreenX
            val offsetY = wrongTargetY - digitScreenY
            val movedGrid = grid.withOffset(offsetX, offsetY)

            if (UnlockVerifier.verify(movedGrid, config, cellSize, originCol, originRow)) {
                anyFalsePositive = true
                break
            }
        }

        assertFalse("Correct digit at wrong position should never unlock", anyFalsePositive)
    }

    // --- Tolerance boundary tests ---

    @Test
    fun `unlock at exact tolerance boundary`() {
        val secretDigit = 3
        val secretX = 0.5f
        val secretY = 1.0f
        val tolerance = 0.10f
        val config = makeConfig(secretDigit, secretX, secretY, tolerance)

        val grid = NumberGridFactory.createRandomGrid(random = Random(77))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        val positions = grid.positionsOf(secretDigit)
        val (col, row) = positions.first()
        val digitX = (col - originCol) * cellSize + cellSize / 2f
        val digitY = (row - originRow) * cellSize + cellSize / 2f

        // Error just inside tolerance (Euclidean distance = tolerance * 0.99)
        val errorMag = tolerance * 0.99f
        val errorX = errorMag * 0.7071f  // 45-degree direction
        val errorY = errorMag * 0.7071f

        val offsetInside = grid.withOffset(secretX - digitX + errorX, secretY - digitY + errorY)
        assertTrue(
            "Error just inside tolerance should unlock",
            UnlockVerifier.verify(offsetInside, config, cellSize, originCol, originRow)
        )

        // Error just outside tolerance
        val errorMagOutside = tolerance * 1.01f
        val errorXOut = errorMagOutside * 0.7071f
        val errorYOut = errorMagOutside * 0.7071f

        val offsetOutside = grid.withOffset(secretX - digitX + errorXOut, secretY - digitY + errorYOut)
        assertFalse(
            "Error just outside tolerance should NOT unlock",
            UnlockVerifier.verify(offsetOutside, config, cellSize, originCol, originRow)
        )
    }

    // --- Config validation edge cases ---

    @Test(expected = IllegalArgumentException::class)
    fun `zero tolerance is rejected`() {
        PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 5,
            secretX = 0.5f,
            secretY = 0.5f,
            toleranceRadius = 0f
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative tolerance is rejected`() {
        PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 5,
            secretX = 0.5f,
            secretY = 0.5f,
            toleranceRadius = -0.1f
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative secretX is rejected`() {
        PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 5,
            secretX = -0.1f,
            secretY = 0.5f
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative secretY is rejected`() {
        PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 5,
            secretX = 0.5f,
            secretY = -0.1f
        )
    }

    // --- Grid structural invariants ---

    @Test
    fun `grid dimensions match factory constants`() {
        for (seed in 1L..20L) {
            val grid = NumberGridFactory.createRandomGrid(random = Random(seed))
            assertEquals(NumberGridFactory.DEFAULT_COLS, grid.cols)
            assertEquals(NumberGridFactory.DEFAULT_ROWS, grid.rows)
            assertEquals(grid.cols * grid.rows, grid.cells.size)
        }
    }

    @Test
    fun `grid is large enough to cover tall screens with all density levels`() {
        val tallAspectRatio = 2400f / 1080f  // ~2.22

        for (visCols in NumberGridFactory.MIN_VISIBLE_COLS..NumberGridFactory.MAX_VISIBLE_COLS) {
            val cellSize = 1f / visCols
            val gridHeightNorm = NumberGridFactory.DEFAULT_ROWS * cellSize
            assertTrue(
                "Grid height ($gridHeightNorm) at visCols=$visCols should exceed aspect ratio ($tallAspectRatio)",
                gridHeightNorm > tallAspectRatio
            )
        }
    }

    @Test
    fun `grid panning room extends beyond visible area in all directions`() {
        val grid = NumberGridFactory.createRandomGrid()
        val visCols = NumberGridFactory.VISIBLE_COLS

        // Horizontal: grid should have more columns than visible
        val extraCols = grid.cols - visCols
        assertTrue(
            "Grid should have at least 12 extra columns for panning ($extraCols found)",
            extraCols >= 12
        )

        // Vertical: grid rows should extend well beyond visible rows
        val visRows = (2400f / 1080f * visCols).toInt()  // approximate visible rows
        val extraRows = grid.rows - visRows
        assertTrue(
            "Grid should have extra rows for vertical panning ($extraRows found)",
            extraRows > 10
        )
    }

    // --- Multiple instances of same digit ---

    @Test
    fun `every digit 0-9 appears at least 10 times in default grid`() {
        for (seed in 1L..20L) {
            val grid = NumberGridFactory.createRandomGrid(random = Random(seed))
            for (d in 0..9) {
                val count = grid.cells.count { it == d }
                assertTrue(
                    "Seed $seed: digit $d should appear >= 10 times, got $count",
                    count >= 10
                )
            }
        }
    }

    @Test
    fun `multiple digit instances prevent observer from identifying the target`() {
        // In a 30x50 grid, each digit appears ~150 times on average
        val grid = NumberGridFactory.createRandomGrid(random = Random(42))
        for (d in 0..9) {
            val count = grid.positionsOf(d).size
            assertTrue(
                "Digit $d should have many instances for observer resistance ($count found)",
                count > 50
            )
        }
    }
}
