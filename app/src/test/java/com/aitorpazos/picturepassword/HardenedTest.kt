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
 * Hardened tests: edge cases, invariants, and adversarial scenarios
 * that complement the existing test suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class HardenedTest {

    // ---- Grid structural edge cases ----

    @Test
    fun `grid cells array shares reference (callers must not mutate)`() {
        // NumberGrid does NOT defensively copy the array for performance.
        // The factory always creates a fresh IntArray, so this is safe in practice.
        // This test documents the behavior: external mutation IS visible.
        val original = IntArray(6) { it }
        val grid = NumberGrid(cols = 3, rows = 2, cells = original)
        original[0] = 99
        assertEquals(
            "Grid shares array reference — mutation is visible (by design)",
            99, grid.digitAt(0, 0)
        )
    }

    @Test
    fun `withOffset does not mutate original grid`() {
        val grid = NumberGridFactory.createRandomGrid(random = Random(1))
        val moved = grid.withOffset(0.5f, 0.5f)
        assertEquals(0f, grid.gridOffsetX, 0.0001f)
        assertEquals(0f, grid.gridOffsetY, 0.0001f)
        assertEquals(0.5f, moved.gridOffsetX, 0.0001f)
        assertEquals(0.5f, moved.gridOffsetY, 0.0001f)
    }

    @Test
    fun `extreme offsets do not crash isDigitAtPoint`() {
        val grid = NumberGridFactory.createRandomGrid(random = Random(42))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        // Offsets way beyond any reasonable drag
        val extremeGrid = grid.withOffset(1000f, -1000f)
        val config = PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 5,
            secretX = 0.5f,
            secretY = 0.5f,
            toleranceRadius = 0.01f
        )
        // Should not crash, just return false
        assertFalse(UnlockVerifier.verify(extremeGrid, config, cellSize, originCol, originRow))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `grid with mismatched cells size throws`() {
        NumberGrid(cols = 3, rows = 2, cells = IntArray(10))
    }

    @Test
    fun `digitAt out of bounds returns 0 gracefully`() {
        val grid = NumberGrid(cols = 2, rows = 2, cells = intArrayOf(1, 2, 3, 4))
        assertEquals(0, grid.digitAt(5, 5))
        assertEquals(0, grid.digitAt(-1, 0))
    }

    // ---- Equality and hashCode ----

    @Test
    fun `grids with same cells but different offsets are not equal`() {
        val cells = intArrayOf(1, 2, 3, 4)
        val g1 = NumberGrid(cols = 2, rows = 2, cells = cells.copyOf(), gridOffsetX = 0f)
        val g2 = NumberGrid(cols = 2, rows = 2, cells = cells.copyOf(), gridOffsetX = 0.1f)
        assertNotEquals(g1, g2)
    }

    @Test
    fun `grids with identical content have same hashCode`() {
        val g1 = NumberGrid(cols = 2, rows = 2, cells = intArrayOf(1, 2, 3, 4))
        val g2 = NumberGrid(cols = 2, rows = 2, cells = intArrayOf(1, 2, 3, 4))
        assertEquals(g1.hashCode(), g2.hashCode())
    }

    @Test
    fun `grid is not equal to null or other types`() {
        val grid = NumberGrid(cols = 2, rows = 2, cells = intArrayOf(1, 2, 3, 4))
        assertNotEquals(grid, null)
        assertNotEquals(grid, "not a grid")
    }

    // ---- Config validation exhaustive ----

    @Test(expected = IllegalArgumentException::class)
    fun `secretX exactly above 1 throws`() {
        PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 5,
            secretX = 1.0001f,
            secretY = 0.5f
        )
    }

    @Test
    fun `secretX at boundaries 0 and 1 are valid`() {
        PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 0,
            secretX = 0f,
            secretY = 0f
        )
        PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 9,
            secretX = 1f,
            secretY = 0f
        )
    }

    @Test
    fun `secretY can be large for tall screens`() {
        val config = PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 5,
            secretX = 0.5f,
            secretY = 2.5f
        )
        assertEquals(2.5f, config.secretY, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tolerance of exactly 0 throws`() {
        PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 5,
            secretX = 0.5f,
            secretY = 0.5f,
            toleranceRadius = 0.0f
        )
    }

    @Test
    fun `very small positive tolerance is accepted`() {
        val config = PicturePasswordConfig(
            imageUri = Uri.parse("content://test"),
            secretNumber = 5,
            secretX = 0.5f,
            secretY = 0.5f,
            toleranceRadius = 0.0001f
        )
        assertEquals(0.0001f, config.toleranceRadius, 0.00001f)
    }

    // ---- Unlock verification edge cases ----

    @Test
    fun `zero-distance drag (digit already on target) unlocks`() {
        val grid = NumberGridFactory.createRandomGrid(random = Random(1))
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f

        for ((col, row) in grid.positionsOf(5)) {
            val digitX = (col - originCol) * cellSize + cellSize / 2f
            val digitY = (row - originRow) * cellSize + cellSize / 2f
            if (digitX in 0f..1f && digitY in 0f..2f) {
                val config = PicturePasswordConfig(
                    imageUri = Uri.parse("content://test"),
                    secretNumber = 5,
                    secretX = digitX.coerceIn(0f, 1f),
                    secretY = digitY,
                    toleranceRadius = 0.06f
                )
                assertTrue(
                    "Zero-distance drag should unlock",
                    UnlockVerifier.verify(grid, config, cellSize, originCol, originRow)
                )
                return
            }
        }
        fail("Should find an on-screen instance of digit 5")
    }

    @Test
    fun `all 10 digits can be used as secret number`() {
        for (digit in 0..9) {
            val grid = NumberGridFactory.createRandomGrid(random = Random(digit.toLong() + 100))
            val cellSize = 1f / NumberGridFactory.VISIBLE_COLS
            val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
            val originRow = 0f

            val positions = grid.positionsOf(digit)
            assertTrue("Digit $digit should exist in grid", positions.isNotEmpty())

            val (col, row) = positions.first()
            val digitX = (col - originCol) * cellSize + cellSize / 2f
            val digitY = (row - originRow) * cellSize + cellSize / 2f

            val targetX = 0.5f
            val targetY = 1.0f
            val movedGrid = grid.withOffset(targetX - digitX, targetY - digitY)

            val config = PicturePasswordConfig(
                imageUri = Uri.parse("content://test"),
                secretNumber = digit,
                secretX = targetX,
                secretY = targetY,
                toleranceRadius = 0.12f
            )
            assertTrue(
                "Digit $digit should unlock when correctly aligned",
                UnlockVerifier.verify(movedGrid, config, cellSize, originCol, originRow)
            )
        }
    }

    // ---- Statistical attack resistance ----

    @Test
    fun `no digit is disproportionately close to center across many grids`() {
        val centerHits = IntArray(10)
        val cellSize = 1f / NumberGridFactory.VISIBLE_COLS

        for (seed in 1L..200L) {
            val grid = NumberGridFactory.createRandomGrid(random = Random(seed))
            val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
            val originRow = 0f

            for (digit in 0..9) {
                for ((col, row) in grid.positionsOf(digit)) {
                    val cx = (col - originCol) * cellSize + cellSize / 2f
                    val cy = (row - originRow) * cellSize + cellSize / 2f
                    val dist = kotlin.math.sqrt((cx - 0.5f) * (cx - 0.5f) + (cy - 1.0f) * (cy - 1.0f))
                    if (dist < 0.12f) {
                        centerHits[digit]++
                    }
                }
            }
        }

        val avg = centerHits.average()
        for (d in 0..9) {
            val ratio = centerHits[d] / avg
            assertTrue(
                "Digit $d center-proximity ratio ($ratio) should be between 0.6 and 1.4",
                ratio in 0.6..1.4
            )
        }
    }

    @Test
    fun `grid layout entropy is high across seeds`() {
        val firstRows = mutableSetOf<List<Int>>()
        for (seed in 1L..100L) {
            val grid = NumberGridFactory.createRandomGrid(random = Random(seed))
            val firstRow = (0 until grid.cols).map { grid.digitAt(it, 0) }
            firstRows.add(firstRow)
        }
        assertTrue(
            "First rows should be mostly unique (${firstRows.size}/100)",
            firstRows.size >= 95
        )
    }

    // ---- Factory edge cases ----

    @Test
    fun `factory with minimum dimensions works`() {
        val grid = NumberGridFactory.createRandomGrid(cols = 1, rows = 1, random = Random(1))
        assertEquals(1, grid.cols)
        assertEquals(1, grid.rows)
        assertEquals(1, grid.cells.size)
        assertTrue(grid.cells[0] in 0..9)
    }

    @Test
    fun `factory with large dimensions works`() {
        val grid = NumberGridFactory.createRandomGrid(cols = 100, rows = 100, random = Random(1))
        assertEquals(100, grid.cols)
        assertEquals(100, grid.rows)
        assertEquals(10000, grid.cells.size)
    }

    @Test
    fun `randomVisibleCols is always within bounds over 1000 seeds`() {
        for (seed in 1L..1000L) {
            val cols = NumberGridFactory.randomVisibleCols(Random(seed))
            assertTrue(
                "visCols=$cols out of range",
                cols in NumberGridFactory.MIN_VISIBLE_COLS..NumberGridFactory.MAX_VISIBLE_COLS
            )
        }
    }

    // ---- GridCell legacy compat ----

    @Test
    fun `GridCell rejects digit outside 0-9`() {
        try {
            GridCell(digit = 10, normalizedX = 0.5f, normalizedY = 0.5f)
            fail("Should throw for digit 10")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            GridCell(digit = -1, normalizedX = 0.5f, normalizedY = 0.5f)
            fail("Should throw for digit -1")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `GridCell accepts valid digits`() {
        for (d in 0..9) {
            val cell = GridCell(digit = d, normalizedX = 0f, normalizedY = 0f)
            assertEquals(d, cell.digit)
        }
    }
}
