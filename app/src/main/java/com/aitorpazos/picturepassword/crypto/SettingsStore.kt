package com.aitorpazos.picturepassword.crypto

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores lock screen service preferences:
 * - Whether the lock screen service is enabled
 * - Unlock mode: PICTURE_ONLY, BIOMETRIC_ONLY, BOTH_REQUIRED, EITHER
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

    enum class UnlockMode(val label: String) {
        PICTURE_ONLY("Picture Password only"),
        BIOMETRIC_ONLY("Biometrics only"),
        BOTH_REQUIRED("Both required (2FA)"),
        EITHER("Either unlocks")
    }

    companion object {
        private const val PREFS_FILE = "picture_password_settings"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_UNLOCK_MODE = "unlock_mode"
    }
}
