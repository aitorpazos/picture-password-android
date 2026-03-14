package com.aitorpazos.picturepassword.model

import kotlin.math.sqrt

/**
 * Simple 2D point for normalized coordinates.
 * Avoids dependency on android.graphics.PointF so the model is testable in plain JVM.
 */
data class NormalizedPoint(val x: Float, val y: Float)

/**
 * Represents the state of the number grid overlay.
 *
 * The grid is a large rectangular matrix of digits 0-9 (with repeats) that extends
 * beyond the visible screen area. The user drags the entire grid to align their
 * secret number with the secret point on the image.
 *
 * Grid coordinates are in "cell units" — the view converts them to pixels.
 * The grid is large enough that panning in any direction still shows numbers.
 */
data class NumberGrid(
    /** Number of columns in the grid */
    val cols: Int,
    /** Number of rows in the grid */
    val rows: Int,
    /** Flat array of digits, row-major: cells[row * cols + col] */
    val cells: IntArray,
    /** Current drag offset in normalized screen coordinates (0..1 = full screen) */
    val gridOffsetX: Float = 0f,
    val gridOffsetY: Float = 0f
) {
    init {
        require(cells.size == cols * rows) { "cells size must equal cols * rows" }
    }

    /** Get the digit at a specific grid position */
    fun digitAt(col: Int, row: Int): Int = cells[row * cols + col]

    /**
     * Find all grid positions of a specific digit.
     * Returns list of (col, row) pairs.
     */
    fun positionsOf(digit: Int): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (cells[r * cols + c] == digit) {
                    result.add(c to r)
                }
            }
        }
        return result
    }

    /**
     * Check if any instance of [digit] is within [tolerance] of the target point
     * when the grid is rendered with a given cell size and origin offset.
     *
     * @param digit the secret digit
     * @param targetX target X in normalized screen coords (0..1)
     * @param targetY target Y in normalized screen coords (0..1)
     * @param cellSize cell size as fraction of screen width
     * @param originCol the column index that maps to screen-left before any drag
     * @param originRow the row index that maps to screen-top before any drag
     * @param tolerance max distance in normalized coords
     */
    fun isDigitAtPoint(
        digit: Int,
        targetX: Float,
        targetY: Float,
        cellSize: Float,
        originCol: Float,
        originRow: Float,
        tolerance: Float
    ): Boolean {
        for ((c, r) in positionsOf(digit)) {
            val cx = (c - originCol) * cellSize + gridOffsetX + cellSize / 2f
            val cy = (r - originRow) * cellSize + gridOffsetY + cellSize / 2f
            val dx = cx - targetX
            val dy = cy - targetY
            if (sqrt(dx * dx + dy * dy) <= tolerance) return true
        }
        return false
    }

    fun withOffset(offsetX: Float, offsetY: Float): NumberGrid {
        return copy(gridOffsetX = offsetX, gridOffsetY = offsetY)
    }

    // data class with IntArray needs manual equals/hashCode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NumberGrid) return false
        return cols == other.cols && rows == other.rows &&
                cells.contentEquals(other.cells) &&
                gridOffsetX == other.gridOffsetX && gridOffsetY == other.gridOffsetY
    }

    override fun hashCode(): Int {
        var result = cols
        result = 31 * result + rows
        result = 31 * result + cells.contentHashCode()
        result = 31 * result + gridOffsetX.hashCode()
        result = 31 * result + gridOffsetY.hashCode()
        return result
    }
}

/**
 * Legacy compat — not used by the new rectangular grid, but kept for
 * any code that still references GridCell directly.
 */
data class GridCell(
    val digit: Int,
    val normalizedX: Float,
    val normalizedY: Float
) {
    init {
        require(digit in 0..9) { "Digit must be 0-9" }
    }
}
