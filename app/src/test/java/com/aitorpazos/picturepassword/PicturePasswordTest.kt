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
    fun `grid factory creates 10 digits`() {
        val grid = NumberGridFactory.createRandomGrid()
        assertEquals(10, grid.cellPositions.size)
        val digits = grid.cellPositions.map { it.digit }.sorted()
        assertEquals((0..9).toList(), digits)
    }

    @Test
    fun `scattered grid creates 10 digits`() {
        val grid = NumberGridFactory.createScatteredGrid()
        assertEquals(10, grid.cellPositions.size)
        val digits = grid.cellPositions.map { it.digit }.sorted()
        assertEquals((0..9).toList(), digits)
    }

    @Test
    fun `grid with same seed produces same layout`() {
        val grid1 = NumberGridFactory.createRandomGrid(Random(42))
        val grid2 = NumberGridFactory.createRandomGrid(Random(42))
        assertEquals(grid1.cellPositions, grid2.cellPositions)
    }

    @Test
    fun `different seeds produce different layouts`() {
        val grid1 = NumberGridFactory.createRandomGrid(Random(42))
        val grid2 = NumberGridFactory.createRandomGrid(Random(99))
        assertNotEquals(grid1.cellPositions, grid2.cellPositions)
    }

    @Test
    fun `all positions are within bounds`() {
        repeat(100) {
            val grid = NumberGridFactory.createRandomGrid()
            for (cell in grid.cellPositions) {
                assertTrue("X out of bounds: ${cell.normalizedX}", cell.normalizedX in 0f..1f)
                assertTrue("Y out of bounds: ${cell.normalizedY}", cell.normalizedY in 0f..1f)
            }
        }
    }

    @Test
    fun `getDigitPosition returns correct position with no offset`() {
        val cells = listOf(
            GridCell(5, 0.3f, 0.4f),
            GridCell(3, 0.7f, 0.8f)
        ) + (0..9).filter { it != 5 && it != 3 }.map { GridCell(it, 0.5f, 0.5f) }
        val grid = NumberGrid(cells)

        val pos = grid.getDigitPosition(5)
        assertEquals(0.3f, pos.x, 0.001f)
        assertEquals(0.4f, pos.y, 0.001f)
    }

    @Test
    fun `getDigitPosition applies offset`() {
        val cells = (0..9).map { GridCell(it, 0.5f, 0.5f) }
        val grid = NumberGrid(cells, gridOffsetX = 0.1f, gridOffsetY = -0.2f)

        val pos = grid.getDigitPosition(0)
        assertEquals(0.6f, pos.x, 0.001f)
        assertEquals(0.3f, pos.y, 0.001f)
    }

    @Test
    fun `isDigitAtPoint returns true when within tolerance`() {
        val cells = listOf(GridCell(7, 0.5f, 0.5f)) +
                (0..9).filter { it != 7 }.map { GridCell(it, 0.1f, 0.1f) }
        val grid = NumberGrid(cells)

        assertTrue(grid.isDigitAtPoint(7, 0.5f, 0.5f, 0.06f))
        assertTrue(grid.isDigitAtPoint(7, 0.53f, 0.52f, 0.06f))
    }

    @Test
    fun `isDigitAtPoint returns false when outside tolerance`() {
        val cells = listOf(GridCell(7, 0.5f, 0.5f)) +
                (0..9).filter { it != 7 }.map { GridCell(it, 0.1f, 0.1f) }
        val grid = NumberGrid(cells)

        assertFalse(grid.isDigitAtPoint(7, 0.7f, 0.7f, 0.06f))
    }

    @Test
    fun `withOffset creates new grid with offset`() {
        val grid = NumberGridFactory.createRandomGrid()
        val moved = grid.withOffset(0.1f, 0.2f)

        assertEquals(0.1f, moved.gridOffsetX, 0.001f)
        assertEquals(0.2f, moved.gridOffsetY, 0.001f)
        assertEquals(0f, grid.gridOffsetX, 0.001f) // original unchanged
    }

    @Test
    fun `grid cell rejects invalid digit`() {
        try {
            GridCell(10, 0.5f, 0.5f)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `grid cell rejects negative digit`() {
        try {
            GridCell(-1, 0.5f, 0.5f)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
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
        val config = makeConfig(5, 0.5f, 0.5f)
        val cells = listOf(GridCell(5, 0.5f, 0.5f)) +
                (0..9).filter { it != 5 }.map { GridCell(it, 0.1f, 0.1f) }
        val grid = NumberGrid(cells)

        assertTrue(UnlockVerifier.verify(grid, config))
    }

    @Test
    fun `verify returns true when digit within tolerance`() {
        val config = makeConfig(3, 0.5f, 0.5f, 0.1f)
        val cells = listOf(GridCell(3, 0.45f, 0.48f)) +
                (0..9).filter { it != 3 }.map { GridCell(it, 0.1f, 0.1f) }
        val grid = NumberGrid(cells)

        assertTrue(UnlockVerifier.verify(grid, config))
    }

    @Test
    fun `verify returns false when digit too far from secret point`() {
        val config = makeConfig(5, 0.5f, 0.5f)
        val cells = listOf(GridCell(5, 0.2f, 0.2f)) +
                (0..9).filter { it != 5 }.map { GridCell(it, 0.5f, 0.5f) }
        val grid = NumberGrid(cells)

        assertFalse(UnlockVerifier.verify(grid, config))
    }

    @Test
    fun `verify returns false when wrong digit at secret point`() {
        val config = makeConfig(5, 0.5f, 0.5f)
        val cells = listOf(
            GridCell(3, 0.5f, 0.5f),
            GridCell(5, 0.1f, 0.1f)
        ) + (0..9).filter { it != 3 && it != 5 }.map { GridCell(it, 0.8f, 0.8f) }
        val grid = NumberGrid(cells)

        assertFalse(UnlockVerifier.verify(grid, config))
    }

    @Test
    fun `verify works with grid offset`() {
        val config = makeConfig(7, 0.5f, 0.5f)
        val cells = listOf(GridCell(7, 0.3f, 0.3f)) +
                (0..9).filter { it != 7 }.map { GridCell(it, 0.1f, 0.1f) }
        val grid = NumberGrid(cells, gridOffsetX = 0.2f, gridOffsetY = 0.2f)

        assertTrue(UnlockVerifier.verify(grid, config))
    }

    @Test
    fun `verify fails with insufficient offset`() {
        val config = makeConfig(7, 0.5f, 0.5f)
        val cells = listOf(GridCell(7, 0.3f, 0.3f)) +
                (0..9).filter { it != 7 }.map { GridCell(it, 0.1f, 0.1f) }
        val grid = NumberGrid(cells, gridOffsetX = 0.05f, gridOffsetY = 0.05f)

        assertFalse(UnlockVerifier.verify(grid, config))
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
