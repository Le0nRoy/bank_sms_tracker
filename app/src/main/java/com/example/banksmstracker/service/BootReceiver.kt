package com.example.banksmstracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.banksmstracker.BankSmsTrackerApp

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(BankSmsTrackerApp.PREFS_NAME, Context.MODE_PRIVATE)
            val serviceEnabled = prefs.getBoolean(KEY_BACKGROUND_SERVICE_ENABLED, true)
            if (serviceEnabled) {
                SmsProcessingService.start(context)
            }
        }
    }

    companion object {
        const val KEY_BACKGROUND_SERVICE_ENABLED = "background_service_enabled"
    }
}
