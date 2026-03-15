package com.aitorpazos.picturepassword.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.ParcelFileDescriptor
import java.security.MessageDigest

/**
 * Utility for reading the system wallpaper and computing
 * a SHA-256 hash for change detection.
 *
 * Strategy (in priority order):
 * 1. getWallpaperFile(FLAG_LOCK)   — lock screen wallpaper file (API 24+)
 * 2. getWallpaperFile(FLAG_SYSTEM) — home screen wallpaper file (API 24+)
 * 3. WallpaperManager.getDrawable() — legacy drawable (pre-24 or fallback)
 *
 * getWallpaperFile() works without READ_EXTERNAL_STORAGE on most devices
 * because it goes through WallpaperManagerService (a system service) which
 * opens the file on the caller's behalf. Only some OEMs restrict this.
 */
object WallpaperHelper {

    /**
     * Get the system wallpaper as a Bitmap.
     * Tries lock screen first, then home screen, then legacy drawable.
     * Returns null if wallpaper can't be read (permission denied, live wallpaper, etc.).
     */
    fun getLockScreenBitmap(context: Context): Bitmap? {
        return try {
            val wm = WallpaperManager.getInstance(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Strategy 1: Lock screen wallpaper file (most reliable for lock screen)
                val lockBitmap = readWallpaperFile(wm, WallpaperManager.FLAG_LOCK)
                if (lockBitmap != null) return lockBitmap

                // Strategy 2: System (home) wallpaper file
                // On many devices, if no separate lock wallpaper is set,
                // the system wallpaper is used for both.
                val systemBitmap = readWallpaperFile(wm, WallpaperManager.FLAG_SYSTEM)
                if (systemBitmap != null) return systemBitmap
            }

            // Strategy 3: Legacy getDrawable() — works on older APIs
            // and as a last resort on newer ones
            val drawable = wm.drawable
            val bitmapFromDrawable = (drawable as? BitmapDrawable)?.bitmap
            if (bitmapFromDrawable != null) return bitmapFromDrawable

            // Strategy 4: getFastDrawable() — lighter weight, may work when getDrawable() doesn't
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1) {
                val fast = wm.fastDrawable
                (fast as? BitmapDrawable)?.bitmap
            } else {
                null
            }
        } catch (_: SecurityException) {
            // Permission denied — caller should request READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Read wallpaper via getWallpaperFile() which returns a ParcelFileDescriptor.
     * This avoids the need for READ_EXTERNAL_STORAGE on many devices because
     * the WallpaperManagerService opens the file descriptor on our behalf.
     *
     * Returns null if the wallpaper is a live wallpaper, not set, or inaccessible.
     */
    private fun readWallpaperFile(wm: WallpaperManager, which: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null

        var pfd: ParcelFileDescriptor? = null
        return try {
            pfd = wm.getWallpaperFile(which)
            if (pfd != null) {
                val options = BitmapFactory.Options().apply {
                    // Subsample large wallpapers to avoid OOM
                    // First pass: just read dimensions
                    inJustDecodeBounds = true
                }
                val fd1 = pfd.fileDescriptor
                BitmapFactory.decodeFileDescriptor(fd1, null, options)

                // Calculate subsample: target max 2048px on longest side
                val maxDim = maxOf(options.outWidth, options.outHeight)
                options.inSampleSize = if (maxDim > 2048) (maxDim / 2048).coerceAtLeast(1) else 1
                options.inJustDecodeBounds = false

                // We need a fresh file descriptor position — close and re-open
                pfd.close()
                pfd = wm.getWallpaperFile(which)
                if (pfd != null) {
                    BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                } else {
                    null
                }
            } else {
                null  // No static wallpaper set for this flag (could be live wallpaper)
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            try { pfd?.close() } catch (_: Exception) {}
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
        } catch (_: Exception) {
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
