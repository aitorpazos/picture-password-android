package com.aitorpazos.picturepassword.crypto

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores lock screen service preferences:
 * - Whether the lock screen service is enabled
 * - Unlock mode: PICTURE_ONLY, BIOMETRIC_ONLY, BOTH_REQUIRED, EITHER
 * - Image source: CUSTOM (user-picked) or SYSTEM_WALLPAPER (device lock screen wallpaper)
 * - Wallpaper hash: SHA-256 hash of the system wallpaper at setup time (for change detection)
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var unlockMode: UnlockMode
        get() {
            val ordinal = prefs.getInt(KEY_UNLOCK_MODE, UnlockMode.EITHER.ordinal)
            return UnlockMode.entries.getOrElse(ordinal) { UnlockMode.EITHER }
        }
        set(value) = prefs.edit().putInt(KEY_UNLOCK_MODE, value.ordinal).apply()

    var imageSource: ImageSource
        get() {
            val ordinal = prefs.getInt(KEY_IMAGE_SOURCE, ImageSource.CUSTOM.ordinal)
            return ImageSource.entries.getOrElse(ordinal) { ImageSource.CUSTOM }
        }
        set(value) = prefs.edit().putInt(KEY_IMAGE_SOURCE, value.ordinal).apply()

    /** SHA-256 hash of the system wallpaper bitmap at setup time */
    var wallpaperHash: String
        get() = prefs.getString(KEY_WALLPAPER_HASH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WALLPAPER_HASH, value).apply()

    /** Whether a wallpaper change has been detected and user hasn't re-setup yet */
    var wallpaperChanged: Boolean
        get() = prefs.getBoolean(KEY_WALLPAPER_CHANGED, false)
        set(value) = prefs.edit().putBoolean(KEY_WALLPAPER_CHANGED, value).apply()

    enum class UnlockMode(val label: String) {
        PICTURE_ONLY("Picture Password only"),
        BIOMETRIC_ONLY("Biometrics only"),
        BOTH_REQUIRED("Both required (2FA)"),
        EITHER("Either unlocks")
    }

    enum class ImageSource(val label: String) {
        CUSTOM("Custom image"),
        SYSTEM_WALLPAPER("System lock screen wallpaper")
    }

    companion object {
        private const val PREFS_FILE = "picture_password_settings"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_UNLOCK_MODE = "unlock_mode"
        private const val KEY_IMAGE_SOURCE = "image_source"
        private const val KEY_WALLPAPER_HASH = "wallpaper_hash"
        private const val KEY_WALLPAPER_CHANGED = "wallpaper_changed"
    }
}
