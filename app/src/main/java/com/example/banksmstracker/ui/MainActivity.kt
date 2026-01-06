package com.example.banksmstracker.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.banksmstracker.BankSmsTrackerApp
import com.example.banksmstracker.R
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.ImportResult
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { importConfig(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupButtons()
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

        findViewById<Button>(R.id.btnIgnoreRules).setOnClickListener {
            startActivity(Intent(this, IgnoreRulesActivity::class.java))
        }

        findViewById<Button>(R.id.btnSmsExport).setOnClickListener {
            startActivity(Intent(this, SmsExportActivity::class.java))
        }

        findViewById<Button>(R.id.btnThemeToggle).setOnClickListener {
            showThemeDialog()
        }
    }

    private fun showThemeDialog() {
        val options = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.light_mode),
            getString(R.string.dark_mode)
        )

        val prefs = getSharedPreferences(BankSmsTrackerApp.PREFS_NAME, MODE_PRIVATE)
        val currentMode = prefs.getInt(BankSmsTrackerApp.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val currentIndex = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> 0
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val newMode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                prefs.edit().putInt(BankSmsTrackerApp.KEY_THEME_MODE, newMode).apply()
                AppCompatDelegate.setDefaultNightMode(newMode)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
