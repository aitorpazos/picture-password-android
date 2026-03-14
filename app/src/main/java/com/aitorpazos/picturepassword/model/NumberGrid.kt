package com.aitorpazos.picturepassword.model

import kotlin.math.sqrt

/**
 * Simple 2D point for normalized coordinates.
 * Avoids dependency on android.graphics.PointF so the model is testable in plain JVM.
 */
data class NormalizedPoint(val x: Float, val y: Float)

/**
 * Represents the state of the number grid overlay.
 * The grid contains digits 0-9 placed at random positions.
 * The user drags the entire grid to align their secret number with the secret point.
 */
data class NumberGrid(
    val cellPositions: List<GridCell>,
    val gridOffsetX: Float = 0f,
    val gridOffsetY: Float = 0f
) {
    /**
     * Get the current position of a specific digit accounting for the drag offset.
     * Returns normalized coordinates (0.0-1.0).
     */
    fun getDigitPosition(digit: Int): NormalizedPoint {
        val cell = cellPositions.first { it.digit == digit }
        return NormalizedPoint(cell.normalizedX + gridOffsetX, cell.normalizedY + gridOffsetY)
    }

    /**
     * Check if a specific digit is within tolerance of a target point.
     */
    fun isDigitAtPoint(digit: Int, targetX: Float, targetY: Float, tolerance: Float): Boolean {
        val pos = getDigitPosition(digit)
        val dx = pos.x - targetX
        val dy = pos.y - targetY
        return sqrt(dx * dx + dy * dy) <= tolerance
    }

    fun withOffset(offsetX: Float, offsetY: Float): NumberGrid {
        return copy(gridOffsetX = offsetX, gridOffsetY = offsetY)
    }
}

data class GridCell(
    val digit: Int,
    val normalizedX: Float,
    val normalizedY: Float
) {
    init {
        require(digit in 0..9) { "Digit must be 0-9" }
    }
}
