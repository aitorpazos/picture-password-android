package com.aitorpazos.picturepassword.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import java.security.MessageDigest

/**
 * Utility for reading the system lock screen wallpaper and computing
 * a SHA-256 hash for change detection.
 */
object WallpaperHelper {

    /**
     * Get the system lock screen wallpaper as a Bitmap.
     * Falls back to the home wallpaper if lock-specific wallpaper isn't available.
     * Returns null if wallpaper can't be read (permission denied, live wallpaper, etc.).
     */
    fun getLockScreenBitmap(context: Context): Bitmap? {
        return try {
            val wm = WallpaperManager.getInstance(context)

            // Try lock screen wallpaper first (API 24+)
            val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.getDrawable()  // This returns the current wallpaper
            } else {
                wm.drawable
            }

            (drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compute a SHA-256 hash of the current system wallpaper.
     * Returns empty string if wallpaper can't be read.
     *
     * We sample pixels rather than hashing the entire bitmap for performance.
     */
    fun computeWallpaperHash(context: Context): String {
        val bitmap = getLockScreenBitmap(context) ?: return ""
        return try {
            val digest = MessageDigest.getInstance("SHA-256")

            // Sample a grid of pixels for a fast but reliable hash
            val w = bitmap.width
            val h = bitmap.height
            val stepX = (w / 32).coerceAtLeast(1)
            val stepY = (h / 32).coerceAtLeast(1)

            for (y in 0 until h step stepY) {
                for (x in 0 until w step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    digest.update((pixel shr 24).toByte())
                    digest.update((pixel shr 16).toByte())
                    digest.update((pixel shr 8).toByte())
                    digest.update(pixel.toByte())
                }
            }

            // Also include dimensions for uniqueness
            digest.update(w.toByte())
            digest.update((w shr 8).toByte())
            digest.update(h.toByte())
            digest.update((h shr 8).toByte())

            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Check if the current system wallpaper differs from the stored hash.
     * Returns true if wallpaper has changed or can't be determined.
     */
    fun hasWallpaperChanged(context: Context, storedHash: String): Boolean {
        if (storedHash.isEmpty()) return false  // No stored hash = nothing to compare
        val currentHash = computeWallpaperHash(context)
        if (currentHash.isEmpty()) return false  // Can't read wallpaper = assume unchanged
        return currentHash != storedHash
    }
}
