package com.aitorpazos.picturepassword.model

import android.net.Uri

/**
 * Represents the stored Picture Password configuration.
 * @param imageUri URI of the selected background image
 * @param secretNumber The digit (0-9) the user chose as their secret
 * @param secretX X coordinate as fraction of view width (0.0-1.0)
 * @param secretY Y coordinate as fraction of view width (0.0 to aspectRatio; can exceed 1.0 on tall screens)
 * @param toleranceRadius Tolerance radius in width-fraction units (default ~6% of screen width)
 */
data class PicturePasswordConfig(
    val imageUri: Uri,
    val secretNumber: Int,
    val secretX: Float,
    val secretY: Float,
    val toleranceRadius: Float = DEFAULT_TOLERANCE
) {
    init {
        require(secretNumber in 0..9) { "Secret number must be 0-9" }
        require(secretX in 0f..1f) { "secretX must be normalized 0.0-1.0" }
        require(secretY >= 0f) { "secretY must be non-negative" }
        require(toleranceRadius > 0f) { "toleranceRadius must be positive" }
    }

    companion object {
        /** Tolerance radius in width-fraction units (~6% of screen width ≈ ~0.35 cell).
         *  Tighter than the original 12% to require more precise positioning. */
        const val DEFAULT_TOLERANCE = 0.06f
    }
}
