package com.aitorpazos.picturepassword.crypto

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aitorpazos.picturepassword.model.PicturePasswordConfig

/**
 * Securely stores and retrieves the Picture Password configuration
 * using EncryptedSharedPreferences (AES256-SIV for keys, AES256-GCM for values).
 */
class PasswordStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(config: PicturePasswordConfig) {
        prefs.edit()
            .putString(KEY_IMAGE_URI, config.imageUri.toString())
            .putInt(KEY_SECRET_NUMBER, config.secretNumber)
            .putFloat(KEY_SECRET_X, config.secretX)
            .putFloat(KEY_SECRET_Y, config.secretY)
            .putFloat(KEY_TOLERANCE, config.toleranceRadius)
            .putBoolean(KEY_IS_CONFIGURED, true)
            .apply()
    }

    fun load(): PicturePasswordConfig? {
        if (!prefs.getBoolean(KEY_IS_CONFIGURED, false)) return null

        val uriStr = prefs.getString(KEY_IMAGE_URI, null) ?: return null
        return try {
            PicturePasswordConfig(
                imageUri = Uri.parse(uriStr),
                secretNumber = prefs.getInt(KEY_SECRET_NUMBER, 0),
                secretX = prefs.getFloat(KEY_SECRET_X, 0.5f),
                secretY = prefs.getFloat(KEY_SECRET_Y, 0.5f),
                toleranceRadius = prefs.getFloat(KEY_TOLERANCE, PicturePasswordConfig.DEFAULT_TOLERANCE)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun isConfigured(): Boolean = prefs.getBoolean(KEY_IS_CONFIGURED, false)

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE = "picture_password_config"
        private const val KEY_IMAGE_URI = "image_uri"
        private const val KEY_SECRET_NUMBER = "secret_number"
        private const val KEY_SECRET_X = "secret_x"
        private const val KEY_SECRET_Y = "secret_y"
        private const val KEY_TOLERANCE = "tolerance"
        private const val KEY_IS_CONFIGURED = "is_configured"
    }
}
