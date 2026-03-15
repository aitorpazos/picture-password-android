package com.aitorpazos.picturepassword.model

/**
 * Verifier for Picture Password unlock attempts.
 *
 * Works with the rectangular grid model: checks whether any instance of the
 * secret digit, after the user's drag, lands within tolerance of the secret point.
 */
object UnlockVerifier {

    /**
     * Compute the centred origin row for a given grid, visible column count,
     * and screen aspect ratio (height / width).
     * This must match the calculation in NumberGridView.onDraw().
     */
    fun computeOriginRow(grid: NumberGrid, visibleCols: Int, aspectRatio: Float): Float {
        val visibleRows = (aspectRatio * visibleCols).toInt()
        return (grid.rows - visibleRows) / 2f
    }

    /**
     * Check if the current grid state represents a valid unlock.
     *
     * @param grid The current number grid with user's drag offset applied
     * @param config The stored password configuration
     * @param cellSize Cell size as fraction of screen width (derived from visible cols)
     * @param originCol The column index that maps to screen-left edge before drag
     * @param originRow The row index that maps to screen-top edge before drag
     * @return true if any instance of the secret number is positioned over the secret
     *         point within tolerance
     */
    fun verify(
        grid: NumberGrid,
        config: PicturePasswordConfig,
        cellSize: Float = 1f / NumberGridFactory.VISIBLE_COLS,
        originCol: Float = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f,
        originRow: Float = 0f
    ): Boolean {
        return grid.isDigitAtPoint(
            digit = config.secretNumber,
            targetX = config.secretX,
            targetY = config.secretY,
            cellSize = cellSize,
            originCol = originCol,
            originRow = originRow,
            tolerance = config.toleranceRadius
        )
    }
}
