package com.example.banksmstracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.banksmstracker.R
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.util.Constants

class CheckSendersActivity : BaseActivity() {

    private lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_senders)

        textView = findViewById(R.id.textViewCheckSenders)

        if (checkSmsPermission()) {
            checkSenders()
        } else {
            requestSmsPermission()
        }
    }

    private fun checkSmsPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_SMS),
            Constants.RequestCodes.SMS_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.RequestCodes.SMS_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkSenders()
            } else {
                textView.text = getString(R.string.sms_permission_denied_check)
            }
        }
    }

    private fun checkSenders() {
        try {
            val configuredSenders = ConfigRepository.config.senders.flatMap { it.addresses }.toSet()
            val smsSenders = getSmsSenders()

            val matchingSenders = smsSenders.intersect(configuredSenders)
            val nonMatchingSenders = smsSenders - configuredSenders

            val result = StringBuilder()
            result.append(getString(R.string.senders_check_title))

            if (matchingSenders.isNotEmpty()) {
                result.append(getString(R.string.success_indicator))
                result.append(" ")
                result.append(getString(R.string.senders_with_rules_found))
                matchingSenders.forEach { sender ->
                    result.append("• $sender\n")
                }
                result.append("\n")
            } else {
                result.append(getString(R.string.error_indicator))
                result.append(" ")
                result.append(getString(R.string.no_senders_with_rules_found))
            }

            if (nonMatchingSenders.isNotEmpty()) {
                result.append(getString(R.string.all_sms_senders_without_rules))
                nonMatchingSenders.sorted().forEach { sender ->
                    result.append("• $sender\n")
                }
            }

            textView.text = result.toString()
        } catch (e: Exception) {
            textView.text = getString(R.string.error_with_message, e.message ?: "")
            e.printStackTrace()
        }
    }

    private fun getSmsSenders(): Set<String> {
        val senders = mutableSetOf<String>()
        val uri = Uri.parse("content://sms")

        val cursor: Cursor? = contentResolver.query(
            uri,
            arrayOf("DISTINCT address"),
            null,
            null,
            "address ASC LIMIT 10000"
        )

        cursor?.use {
            val addressColumn = it.getColumnIndex("address")
            while (it.moveToNext()) {
                val address = it.getString(addressColumn)
                if (!address.isNullOrBlank()) {
                    senders.add(address)
                }
            }
        }

        return senders
    }
}
