package com.example.banksmstracker.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.banksmstracker.BankSmsTrackerApp
import com.example.banksmstracker.BuildConfig
import com.example.banksmstracker.R
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.ImportResult
import com.example.banksmstracker.service.BootReceiver
import com.example.banksmstracker.service.SmsProcessingService
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    companion object {
        const val PREFS_TERMS = "app_terms"
        const val KEY_TERMS_AGREED = "user_agreed_to_terms"
    }

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { importConfig(it) }
    }

    private val onboardingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After onboarding finishes, mark completed and refresh banner
        getSharedPreferences(OnboardingActivity.PREFS_ONBOARDING, MODE_PRIVATE)
            .edit().putBoolean(OnboardingActivity.KEY_ONBOARDING_COMPLETED, true).apply()
        updateSmsPermissionBanner()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupButtons()
        checkTermsAndOnboarding()
        startBackgroundServiceIfEnabled()
    }

    override fun onResume() {
        super.onResume()
        updateSmsPermissionBanner()
    }

    private fun updateSmsPermissionBanner() {
        val banner = findViewById<TextView>(R.id.tvSmsPermissionWarning)
        val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
        banner.visibility = if (hasSms) View.GONE else View.VISIBLE
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnCategories).setOnClickListener {
            startActivity(Intent(this, CategoriesActivity::class.java))
        }

        findViewById<Button>(R.id.btnSenders).setOnClickListener {
            startActivity(Intent(this, SendersActivity::class.java))
        }

        findViewById<Button>(R.id.btnCheckSenders).setOnClickListener {
            startActivity(Intent(this, CheckSendersActivity::class.java))
        }

        findViewById<Button>(R.id.btnApplyRules).setOnClickListener {
            startActivity(Intent(this, ApplyRulesActivity::class.java))
        }

        findViewById<Button>(R.id.btnExportConfig).setOnClickListener {
            exportConfig()
        }

        findViewById<Button>(R.id.btnImportConfig).setOnClickListener {
            importFileLauncher.launch("application/json")
        }

        findViewById<Button>(R.id.btnPayments).setOnClickListener {
            startActivity(Intent(this, PaymentsActivity::class.java))
        }

        findViewById<Button>(R.id.btnIncomes).setOnClickListener {
            startActivity(Intent(this, IncomesActivity::class.java))
        }

        findViewById<Button>(R.id.btnRegexBuilder).setOnClickListener {
            startActivity(Intent(this, RegexBuilderActivity::class.java))
        }

        findViewById<Button>(R.id.btnBugReport).setOnClickListener {
            startActivity(Intent(this, BugReportActivity::class.java))
        }

        val btnSmsExport = findViewById<Button>(R.id.btnSmsExport)
        if (!BuildConfig.DEBUG) {
            btnSmsExport.visibility = View.GONE
        } else {
            btnSmsExport.setOnClickListener {
                startActivity(Intent(this, SmsExportActivity::class.java))
            }
        }

        findViewById<Button>(R.id.btnThemeToggle).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun exportConfig() {
        lifecycleScope.launch {
            try {
                val (file, uri) = ConfigRepository.shareConfigFile(this@MainActivity)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.config_export_success, file.absolutePath),
                    Toast.LENGTH_SHORT
                ).show()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_config_title)))
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.config_export_failed, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startBackgroundServiceIfEnabled() {
        val prefs = getSharedPreferences(BankSmsTrackerApp.PREFS_NAME, MODE_PRIVATE)
        val serviceEnabled = prefs.getBoolean(BootReceiver.KEY_BACKGROUND_SERVICE_ENABLED, true)
        if (serviceEnabled) {
            SmsProcessingService.start(this)
        }
    }

    private fun checkTermsAndOnboarding() {
        val termsPrefs = getSharedPreferences(PREFS_TERMS, MODE_PRIVATE)
        val onboardingPrefs = getSharedPreferences(OnboardingActivity.PREFS_ONBOARDING, MODE_PRIVATE)
        val termsAgreed = termsPrefs.getBoolean(KEY_TERMS_AGREED, false)
        val onboardingDone = onboardingPrefs.getBoolean(OnboardingActivity.KEY_ONBOARDING_COMPLETED, false)

        if (!termsAgreed) {
            // Show terms first; after agreement, trigger onboarding if not yet done
            showTermsDialogWithOnboardingCallback(!onboardingDone)
        } else if (!onboardingDone) {
            startOnboarding()
        }
    }

    private fun showTermsDialogWithOnboardingCallback(launchOnboardingAfter: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(R.string.terms_title)
            .setMessage(R.string.terms_message)
            .setPositiveButton(R.string.terms_agree) { _, _ ->
                getSharedPreferences(PREFS_TERMS, MODE_PRIVATE)
                    .edit().putBoolean(KEY_TERMS_AGREED, true).apply()
                if (launchOnboardingAfter) {
                    startOnboarding()
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun startOnboarding() {
        onboardingLauncher.launch(Intent(this, OnboardingActivity::class.java))
    }

    fun showTermsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.terms_title)
            .setMessage(R.string.terms_message)
            .setPositiveButton(R.string.terms_agree) { _, _ ->
                getSharedPreferences(PREFS_TERMS, MODE_PRIVATE)
                    .edit().putBoolean(KEY_TERMS_AGREED, true).apply()
            }
            .setCancelable(false)
            .show()
    }

    private fun importConfig(uri: android.net.Uri) {
        lifecycleScope.launch {
            val result = ConfigRepository.importConfigFromUri(this@MainActivity, uri)
            when (result) {
                is ImportResult.Success -> {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.config_import_success, result.toString()),
                        Toast.LENGTH_LONG
                    ).show()
                }
                is ImportResult.Error -> {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.config_import_failed, result.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
