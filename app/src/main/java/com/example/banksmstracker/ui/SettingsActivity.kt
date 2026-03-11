package com.example.banksmstracker.ui

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.banksmstracker.BankSmsTrackerApp
import com.example.banksmstracker.R

class SettingsActivity : BaseActivity() {

    private lateinit var radioGroupTheme: RadioGroup
    private lateinit var radioGroupLanguage: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        radioGroupTheme = findViewById(R.id.radioGroupTheme)
        radioGroupLanguage = findViewById(R.id.radioGroupLanguage)

        setupThemeSelection()
        setupLanguageSelection()
        setupPrivacySection()
    }

    private fun setupThemeSelection() {
        val prefs = getSharedPreferences(BankSmsTrackerApp.PREFS_NAME, MODE_PRIVATE)
        val currentMode = prefs.getInt(BankSmsTrackerApp.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> findViewById<RadioButton>(R.id.radioThemeSystem).isChecked =
                true
            AppCompatDelegate.MODE_NIGHT_NO -> findViewById<RadioButton>(R.id.radioThemeLight).isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> findViewById<RadioButton>(R.id.radioThemeDark).isChecked = true
        }

        radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.radioThemeSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                R.id.radioThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.radioThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.edit().putInt(BankSmsTrackerApp.KEY_THEME_MODE, newMode).apply()
            AppCompatDelegate.setDefaultNightMode(newMode)
        }
    }

    private fun setupLanguageSelection() {
        val prefs = getSharedPreferences(BankSmsTrackerApp.PREFS_NAME, MODE_PRIVATE)
        val currentLanguage = prefs.getString(BankSmsTrackerApp.KEY_LANGUAGE, "") ?: ""

        when (currentLanguage) {
            "" -> findViewById<RadioButton>(R.id.radioLanguageSystem).isChecked = true
            "en" -> findViewById<RadioButton>(R.id.radioLanguageEnglish).isChecked = true
            "ru" -> findViewById<RadioButton>(R.id.radioLanguageRussian).isChecked = true
        }

        radioGroupLanguage.setOnCheckedChangeListener { _, checkedId ->
            val newLanguage = when (checkedId) {
                R.id.radioLanguageSystem -> ""
                R.id.radioLanguageEnglish -> "en"
                R.id.radioLanguageRussian -> "ru"
                else -> ""
            }
            prefs.edit().putString(BankSmsTrackerApp.KEY_LANGUAGE, newLanguage).apply()
            applyLanguage(newLanguage)
        }
    }

    private fun setupPrivacySection() {
        findViewById<Button>(R.id.btnViewTerms).setOnClickListener {
            showTermsDialog()
        }
    }

    private fun showTermsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.terms_title)
            .setMessage(R.string.terms_message)
            .setPositiveButton(R.string.terms_agree, null)
            .show()
    }

    private fun applyLanguage(languageCode: String) {
        val localeList = if (languageCode.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
