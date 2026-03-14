package com.aitorpazos.picturepassword.model

/**
 * Verifier for Picture Password unlock attempts.
 */
object UnlockVerifier {

    /**
     * Check if the current grid state represents a valid unlock.
     *
     * @param grid The current number grid with user's drag offset applied
     * @param config The stored password configuration
     * @return true if the secret number is positioned over the secret point within tolerance
     */
    fun verify(grid: NumberGrid, config: PicturePasswordConfig): Boolean {
        return grid.isDigitAtPoint(
            digit = config.secretNumber,
            targetX = config.secretX,
            targetY = config.secretY,
            tolerance = config.toleranceRadius
        )
    }
}
