package com.aitorpazos.picturepassword

import android.net.Uri
import com.aitorpazos.picturepassword.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

class NumberGridTest {

    @Test
    fun `grid factory creates correct dimensions`() {
        val grid = NumberGridFactory.createRandomGrid()
        assertEquals(NumberGridFactory.DEFAULT_COLS, grid.cols)
        assertEquals(NumberGridFactory.DEFAULT_ROWS, grid.rows)
        assertEquals(NumberGridFactory.DEFAULT_COLS * NumberGridFactory.DEFAULT_ROWS, grid.cells.size)
    }

    @Test
    fun `grid factory with custom dimensions`() {
        val grid = NumberGridFactory.createRandomGrid(cols = 8, rows = 10)
        assertEquals(8, grid.cols)
        assertEquals(10, grid.rows)
        assertEquals(80, grid.cells.size)
    }

    @Test
    fun `all cells contain digits 0-9`() {
        val grid = NumberGridFactory.createRandomGrid()
        for (cell in grid.cells) {
            assertTrue("Digit out of range: $cell", cell in 0..9)
        }
    }

    @Test
    fun `grid contains repeated digits`() {
        val grid = NumberGridFactory.createRandomGrid()
        // With 240 cells and 10 possible digits, each digit should appear many times
        for (d in 0..9) {
            val count = grid.cells.count { it == d }
            assertTrue("Digit $d should appear multiple times, got $count", count > 1)
        }
    }

    @Test
    fun `grid with same seed produces same layout`() {
        val grid1 = NumberGridFactory.createRandomGrid(random = Random(42))
        val grid2 = NumberGridFactory.createRandomGrid(random = Random(42))
        assertTrue(grid1.cells.contentEquals(grid2.cells))
    }

    @Test
    fun `different seeds produce different layouts`() {
        val grid1 = NumberGridFactory.createRandomGrid(random = Random(42))
        val grid2 = NumberGridFactory.createRandomGrid(random = Random(99))
        assertFalse(grid1.cells.contentEquals(grid2.cells))
    }

    @Test
    fun `digitAt returns correct value`() {
        val cells = IntArray(12) { it % 10 }  // 0,1,2,3,4,5,6,7,8,9,0,1
        val grid = NumberGrid(cols = 4, rows = 3, cells = cells)
        assertEquals(0, grid.digitAt(0, 0))
        assertEquals(3, grid.digitAt(3, 0))
        assertEquals(4, grid.digitAt(0, 1))
        assertEquals(9, grid.digitAt(1, 2))
    }

    @Test
    fun `positionsOf finds all instances of a digit`() {
        // 3x2 grid: [5, 3, 5, 7, 5, 2]
        val cells = intArrayOf(5, 3, 5, 7, 5, 2)
        val grid = NumberGrid(cols = 3, rows = 2, cells = cells)
        val positions = grid.positionsOf(5)
        assertEquals(3, positions.size)
        assertTrue(positions.contains(0 to 0))
        assertTrue(positions.contains(2 to 0))
        assertTrue(positions.contains(1 to 1))
    }

    @Test
    fun `positionsOf returns empty for missing digit`() {
        val cells = intArrayOf(1, 2, 3, 4)
        val grid = NumberGrid(cols = 2, rows = 2, cells = cells)
        assertTrue(grid.positionsOf(9).isEmpty())
    }

    @Test
    fun `isDigitAtPoint returns true when digit cell aligns with target`() {
        // 4x4 grid, digit 7 at position (1, 1)
        val cells = IntArray(16) { 0 }
        cells[1 * 4 + 1] = 7  // row=1, col=1
        val grid = NumberGrid(cols = 4, rows = 4, cells = cells)

        // cellSize = 1/6 ≈ 0.1667
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = 0f
        val originRow = 0f

        // Centre of cell (1,1) = (1 * cellSize + cellSize/2, 1 * cellSize + cellSize/2)
        val expectedX = 1 * cellSize + cellSize / 2f
        val expectedY = 1 * cellSize + cellSize / 2f

        assertTrue(grid.isDigitAtPoint(7, expectedX, expectedY, cellSize, originCol, originRow, 0.06f))
    }

    @Test
    fun `isDigitAtPoint returns false when digit is far from target`() {
        val cells = IntArray(16) { 0 }
        cells[0] = 7  // row=0, col=0
        val grid = NumberGrid(cols = 4, rows = 4, cells = cells)

        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        // Target is far from (0,0)
        assertTrue(!grid.isDigitAtPoint(7, 0.9f, 0.9f, cellSize, 0f, 0f, 0.06f))
    }

    @Test
    fun `withOffset creates new grid preserving cells`() {
        val grid = NumberGridFactory.createRandomGrid()
        val moved = grid.withOffset(0.1f, 0.2f)

        assertEquals(0.1f, moved.gridOffsetX, 0.001f)
        assertEquals(0.2f, moved.gridOffsetY, 0.001f)
        assertEquals(0f, grid.gridOffsetX, 0.001f) // original unchanged
        assertTrue(grid.cells.contentEquals(moved.cells))
    }

    @Test
    fun `randomVisibleCols returns value within range`() {
        for (seed in 1L..100L) {
            val cols = NumberGridFactory.randomVisibleCols(Random(seed))
            assertTrue("randomVisibleCols ($cols) should be >= MIN_VISIBLE_COLS",
                cols >= NumberGridFactory.MIN_VISIBLE_COLS)
            assertTrue("randomVisibleCols ($cols) should be <= MAX_VISIBLE_COLS",
                cols <= NumberGridFactory.MAX_VISIBLE_COLS)
        }
    }

    @Test
    fun `randomVisibleCols produces varying values`() {
        val values = (1L..100L).map { NumberGridFactory.randomVisibleCols(Random(it)) }.toSet()
        assertTrue("randomVisibleCols should produce at least 2 distinct values, got $values",
            values.size >= 2)
    }

    @Test
    fun `grid equality works with IntArray`() {
        val cells1 = intArrayOf(1, 2, 3, 4)
        val cells2 = intArrayOf(1, 2, 3, 4)
        val g1 = NumberGrid(cols = 2, rows = 2, cells = cells1)
        val g2 = NumberGrid(cols = 2, rows = 2, cells = cells2)
        assertEquals(g1, g2)
    }

    @Test
    fun `grid inequality on different cells`() {
        val g1 = NumberGrid(cols = 2, rows = 2, cells = intArrayOf(1, 2, 3, 4))
        val g2 = NumberGrid(cols = 2, rows = 2, cells = intArrayOf(4, 3, 2, 1))
        assertNotEquals(g1, g2)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UnlockVerifierTest {

    private fun makeConfig(number: Int, x: Float, y: Float, tolerance: Float = 0.06f): PicturePasswordConfig {
        return PicturePasswordConfig(
            imageUri = Uri.parse("content://test/image"),
            secretNumber = number,
            secretX = x,
            secretY = y,
            toleranceRadius = tolerance
        )
    }

    @Test
    fun `verify returns true when digit aligned with secret point`() {
        val grid = NumberGridFactory.createRandomGrid(random = Random(1))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        // Find an instance of digit 5 that falls within the visible screen (0..1)
        val positions = grid.positionsOf(5)
        assertTrue("Grid should contain digit 5", positions.isNotEmpty())

        var found = false
        for ((col, row) in positions) {
            val digitScreenX = (col - originCol) * cellSize + cellSize / 2f
            val digitScreenY = (row - originRow) * cellSize + cellSize / 2f
            if (digitScreenX in 0f..1f && digitScreenY in 0f..1f) {
                val config = makeConfig(5, digitScreenX, digitScreenY, 0.06f)
                assertTrue(UnlockVerifier.verify(grid, config, cellSize, originCol, originRow))
                found = true
                break
            }
        }
        assertTrue("Should find at least one on-screen instance of digit 5", found)
    }

    @Test
    fun `verify returns true when digit dragged to secret point`() {
        val grid = NumberGridFactory.createRandomGrid(random = Random(42))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        // Pick a digit and find its position
        val digit = 3
        val positions = grid.positionsOf(digit)
        val (col, row) = positions.first()

        val digitX = (col - originCol) * cellSize + cellSize / 2f
        val digitY = (row - originRow) * cellSize + cellSize / 2f

        // Target is at (0.5, 0.5) — need to drag by the difference
        val targetX = 0.5f
        val targetY = 0.5f
        val offsetX = targetX - digitX
        val offsetY = targetY - digitY

        val movedGrid = grid.withOffset(offsetX, offsetY)
        val config = makeConfig(digit, targetX, targetY, 0.06f)
        assertTrue(UnlockVerifier.verify(movedGrid, config, cellSize, originCol, originRow))
    }

    @Test
    fun `verify returns false when digit too far from secret point`() {
        val grid = NumberGridFactory.createRandomGrid(random = Random(10))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        // Secret point far from any natural position of digit 8
        val config = makeConfig(8, 0.99f, 0.99f, 0.01f)
        // No offset — digit 8 is unlikely to be at (0.99, 0.99) with tiny tolerance
        assertFalse(UnlockVerifier.verify(grid, config, cellSize, originCol, originRow))
    }

    @Test
    fun `verify returns false with wrong digit at secret point`() {
        val grid = NumberGridFactory.createRandomGrid(random = Random(7))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        // Find where digit 2 is ON SCREEN and set config to expect digit 9 there
        val positions = grid.positionsOf(2)
        var digitX = 0f
        var digitY = 0f
        var found = false
        for ((c, r) in positions) {
            val dx = (c - originCol) * cellSize + cellSize / 2f
            val dy = (r - originRow) * cellSize + cellSize / 2f
            if (dx in 0.05f..0.95f && dy >= 0f) {
                digitX = dx
                digitY = dy
                found = true
                break
            }
        }
        assertTrue("Should find an on-screen instance of digit 2", found)

        val config = makeConfig(9, digitX, digitY, 0.02f)
        assertFalse(UnlockVerifier.verify(grid, config, cellSize, originCol, originRow))
    }

    @Test
    fun `verify works with multiple instances of same digit`() {
        // In a 12x20 grid, each digit appears ~24 times on average
        val grid = NumberGridFactory.createRandomGrid(random = Random(55))
        val positions = grid.positionsOf(7)
        assertTrue("Digit 7 should have multiple positions", positions.size > 5)
    }

    @Test
    fun `verify with large tolerance succeeds more easily`() {
        val grid = NumberGridFactory.createRandomGrid(random = Random(100))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        // With a very large tolerance, almost any position should match
        val config = makeConfig(5, 0.5f, 0.5f, 1.0f)
        assertTrue(UnlockVerifier.verify(grid, config, cellSize, originCol, originRow))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PicturePasswordConfigTest {

    @Test
    fun `valid config is created`() {
        val config = PicturePasswordConfig(
            imageUri = Uri.parse("content://test/image"),
            secretNumber = 5,
            secretX = 0.5f,
            secretY = 0.5f
        )
        assertEquals(5, config.secretNumber)
        assertEquals(0.5f, config.secretX, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid number throws`() {
        PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 10,
            secretX = 0.5f,
            secretY = 0.5f
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative number throws`() {
        PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = -1,
            secretX = 0.5f,
            secretY = 0.5f
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of range X throws`() {
        PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 5,
            secretX = 1.5f,
            secretY = 0.5f
        )
    }

    @Test
    fun `boundary values are valid`() {
        val config = PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 0,
            secretX = 0f,
            secretY = 1f
        )
        assertEquals(0, config.secretNumber)
    }

    @Test
    fun `default tolerance is applied`() {
        val config = PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 5,
            secretX = 0.5f,
            secretY = 0.5f
        )
        assertEquals(PicturePasswordConfig.DEFAULT_TOLERANCE, config.toleranceRadius, 0.001f)
    }
}
