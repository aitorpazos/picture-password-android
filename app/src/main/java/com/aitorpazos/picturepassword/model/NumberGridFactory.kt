package com.aitorpazos.picturepassword.model

import kotlin.random.Random

/**
 * Factory to create randomized NumberGrid layouts.
 *
 * The grid is a large rectangular matrix (default 12 cols × 20 rows = 240 cells)
 * that extends well beyond the visible screen. Each cell contains a random digit 0-9.
 * Digits repeat many times across the grid, so an observer cannot tell which specific
 * instance is the user's target — this is key to the BlackBerry Picture Password
 * security model.
 *
 * The visible screen shows roughly a 6×10 window into this larger grid, and the user
 * pans the grid to align their secret number with their secret spot.
 */
object NumberGridFactory {

    /** Total grid columns (much wider than screen so panning never reveals edges) */
    const val DEFAULT_COLS = 30

    /** Total grid rows (much taller than screen so panning never reveals edges) */
    const val DEFAULT_ROWS = 50

    /**
     * How many columns are visible on screen at once.
     * The cell size is derived from: screenWidth / VISIBLE_COLS.
     */
    const val VISIBLE_COLS = 6

    /**
     * Range of visible columns for randomized density between unlock attempts.
     * Lower = bigger digits (fewer on screen), higher = smaller digits (more on screen).
     * This makes shoulder-surfing much harder since the grid looks different each time.
     */
    const val MIN_VISIBLE_COLS = 5
    const val MAX_VISIBLE_COLS = 8

    /**
     * Pick a random visible column count for varied grid density.
     */
    fun randomVisibleCols(random: Random = Random.Default): Int {
        return random.nextInt(MIN_VISIBLE_COLS, MAX_VISIBLE_COLS + 1)
    }

    /**
     * Create a new randomized rectangular number grid.
     *
     * Every cell gets a random digit 0-9. The grid is large enough that
     * the visible viewport is only a portion of the total grid, and panning
     * in any direction reveals more numbers.
     *
     * @param cols total columns (default 12)
     * @param rows total rows (default 20)
     * @param random RNG instance (injectable for deterministic tests)
     */
    fun createRandomGrid(
        cols: Int = DEFAULT_COLS,
        rows: Int = DEFAULT_ROWS,
        random: Random = Random.Default
    ): NumberGrid {
        val cells = IntArray(cols * rows) { random.nextInt(10) }
        return NumberGrid(cols = cols, rows = rows, cells = cells)
    }
}
