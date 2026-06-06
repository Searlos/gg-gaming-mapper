package com.gg.gaming.mapper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Auto start if was active before reboot
            val prefs = context.getSharedPreferences("gg_data", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start", false)
            if (autoStart) {
                val serviceIntent = Intent(context, OverlayService::class.java)
                val profile = prefs.getString("profile", "{}") ?: "{}"
                serviceIntent.putExtra("profile", profile)
                context.startService(serviceIntent)
            }
        }
    }
}