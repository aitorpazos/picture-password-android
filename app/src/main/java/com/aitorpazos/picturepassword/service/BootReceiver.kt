package com.aitorpazos.picturepassword.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aitorpazos.picturepassword.crypto.PasswordStore
import com.aitorpazos.picturepassword.crypto.SettingsStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            val settings = SettingsStore(context)
            val passwordStore = PasswordStore(context)

            if (settings.serviceEnabled && passwordStore.isConfigured()) {
                LockScreenService.start(context)
            }
        }
    }
}
