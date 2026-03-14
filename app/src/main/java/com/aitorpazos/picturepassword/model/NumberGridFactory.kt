package com.aitorpazos.picturepassword.model

import kotlin.random.Random

/**
 * Factory to create randomized NumberGrid layouts.
 * Each time the lock screen is shown, a new random grid is generated
 * so the unlock gesture is always different — preventing shoulder surfing.
 */
object NumberGridFactory {

    private const val GRID_COLS = 5
    private const val GRID_ROWS = 2
    private const val CELL_PADDING = 0.08f
    private const val GRID_START_X = 0.08f
    private const val GRID_START_Y = 0.30f

    /**
     * Create a new randomized number grid.
     * Digits 0-9 are placed in a shuffled 5x2 grid pattern with slight random jitter.
     */
    fun createRandomGrid(random: Random = Random.Default): NumberGrid {
        val digits = (0..9).shuffled(random)
        val cells = mutableListOf<GridCell>()

        val cellWidth = (1f - 2 * GRID_START_X) / GRID_COLS
        val cellHeight = 0.15f

        digits.forEachIndexed { index, digit ->
            val col = index % GRID_COLS
            val row = index / GRID_COLS

            val baseX = GRID_START_X + col * cellWidth + cellWidth / 2
            val baseY = GRID_START_Y + row * cellHeight + cellHeight / 2

            // Add slight random jitter for visual variety
            val jitterX = (random.nextFloat() - 0.5f) * 0.02f
            val jitterY = (random.nextFloat() - 0.5f) * 0.02f

            cells.add(
                GridCell(
                    digit = digit,
                    normalizedX = (baseX + jitterX).coerceIn(CELL_PADDING, 1f - CELL_PADDING),
                    normalizedY = (baseY + jitterY).coerceIn(CELL_PADDING, 1f - CELL_PADDING)
                )
            )
        }

        return NumberGrid(cellPositions = cells)
    }

    /**
     * Create a fully random scattered grid (digits placed anywhere on screen).
     * This provides maximum randomness but may be harder to read.
     */
    fun createScatteredGrid(random: Random = Random.Default): NumberGrid {
        val digits = (0..9).shuffled(random)
        val cells = digits.map { digit ->
            GridCell(
                digit = digit,
                normalizedX = CELL_PADDING + random.nextFloat() * (1f - 2 * CELL_PADDING),
                normalizedY = CELL_PADDING + random.nextFloat() * (1f - 2 * CELL_PADDING)
            )
        }
        return NumberGrid(cellPositions = cells)
    }
}
