package com.example.banksmstracker.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.banksmstracker.BuildConfig
import com.example.banksmstracker.R
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.ImportResult
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupButtons()
        checkTermsAgreement()
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

    private fun checkTermsAgreement() {
        val prefs = getSharedPreferences(PREFS_TERMS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_TERMS_AGREED, false)) {
            showTermsDialog()
        }
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
