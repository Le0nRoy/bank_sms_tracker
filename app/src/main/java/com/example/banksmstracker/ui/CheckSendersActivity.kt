package com.example.banksmstracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.banksmstracker.R
import com.example.banksmstracker.serializer.ConfigLoader

class CheckSendersActivity : AppCompatActivity() {

    private lateinit var textView: TextView
    private val SMS_PERMISSION_REQUEST = 123

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

    private fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_SMS),
            SMS_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkSenders()
            } else {
                textView.text = "SMS permission denied. Cannot check senders."
            }
        }
    }

    private fun checkSenders() {
        try {
            val configJson = assets.open("default_rules.json").bufferedReader().readText()
            val config = ConfigLoader.load(configJson)
            
            val configuredSenders = config.senders.map { it.address }.toSet()
            val smsSenders = getSmsSenders()
            
            val matchingSenders = smsSenders.intersect(configuredSenders)
            val nonMatchingSenders = smsSenders - configuredSenders
            
            val result = StringBuilder()
            result.append("SMS Senders Check Results:\n\n")
            
            if (matchingSenders.isNotEmpty()) {
                result.append("✅ Senders with rules found:\n")
                matchingSenders.forEach { sender ->
                    result.append("• $sender\n")
                }
                result.append("\n")
            } else {
                result.append("❌ No senders with rules found in SMS storage.\n\n")
            }
            
            if (nonMatchingSenders.isNotEmpty()) {
                result.append("📋 All SMS senders (without rules):\n")
                nonMatchingSenders.sorted().forEach { sender ->
                    result.append("• $sender\n")
                }
            }
            
            textView.text = result.toString()
            
        } catch (e: Exception) {
            textView.text = "Error: ${e.message}"
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
            "address ASC"
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
