package com.aitorpazos.picturepassword.model

import android.net.Uri

/**
 * Represents the stored Picture Password configuration.
 * @param imageUri URI of the selected background image
 * @param secretNumber The digit (0-9) the user chose as their secret
 * @param secretX Normalized X coordinate (0.0-1.0) of the secret point on the image
 * @param secretY Normalized Y coordinate (0.0-1.0) of the secret point on the image
 * @param toleranceRadius Normalized radius for unlock tolerance (default ~5% of image dimension)
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
        require(secretY in 0f..1f) { "secretY must be normalized 0.0-1.0" }
        require(toleranceRadius > 0f) { "toleranceRadius must be positive" }
    }

    companion object {
        const val DEFAULT_TOLERANCE = 0.06f
    }
}
