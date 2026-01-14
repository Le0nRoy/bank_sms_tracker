package com.example.banksmstracker

import androidx.appcompat.app.AppCompatDelegate
import com.example.banksmstracker.repository.ConfigRepository

class BankSmsTrackerApp : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        ConfigRepository.load(this)
        applyTheme()
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val themeMode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    companion object {
        const val PREFS_NAME = "bank_sms_tracker_prefs"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LANGUAGE = "language"
    }
}
