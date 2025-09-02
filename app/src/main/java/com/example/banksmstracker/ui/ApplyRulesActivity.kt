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

class ApplyRulesActivity : AppCompatActivity() {

    private lateinit var textView: TextView
    private val SMS_PERMISSION_REQUEST = 124

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apply_rules)

        textView = findViewById(R.id.textViewApplyRules)
        
        if (checkSmsPermission()) {
            applyRules()
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
                applyRules()
            } else {
                textView.text = "SMS permission denied. Cannot apply rules."
            }
        }
    }

    private fun applyRules() {
        try {
            val configJson = assets.open("default_rules.json").bufferedReader().readText()
            val config = ConfigLoader.load(configJson)
            
            val processor = ConfigLoader.createPaymentProcessor(config)
            
            val configuredSenders = config.senders.map { it.address }.toSet()
            val smsMessages = getSmsMessages(configuredSenders)
            
            val result = StringBuilder()
            result.append("Payment Parsing Results:\n\n")
            
            if (smsMessages.isEmpty()) {
                result.append("❌ No SMS messages found from configured senders.\n")
            } else {
                result.append("📱 Found ${smsMessages.size} SMS messages from configured senders.\n\n")
                
                var parsedCount = 0
                var categorizedCount = 0
                
                smsMessages.forEach { (sender, messages) ->
                    result.append("📧 $sender (${messages.size} messages):\n")
                    
                    messages.forEach { message ->
                        try {
                            val payment = processor.processMessage(message)
                            parsedCount++

                            result.append("  ✅ Parsed: ${payment.amount} ${payment.currency}\n")
                            result.append("     Merchant: ${payment.merchant}\n")
                            result.append("     Category: ${payment.categoryId ?: "Uncategorized"}\n")
                            result.append("     Date: ${payment.timestamp}\n")
                            result.append("     Balance: ${payment.balance} ${payment.currency}\n\n")

                            if (payment.categoryId != null) {
                                categorizedCount++
                            }
                        } catch (e: Exception) {
                            result.append("  ❌ Error parsing: ${e.message}\n\n")
                        }
                    }
                }
                
                result.append("\n📊 Summary:\n")
                result.append("• Total messages: ${smsMessages.values.sumOf { it.size }}\n")
                result.append("• Successfully parsed: $parsedCount\n")
                result.append("• Categorized: $categorizedCount\n")
                result.append("• Uncategorized: ${parsedCount - categorizedCount}\n")
            }
            
            textView.text = result.toString()
            
        } catch (e: Exception) {
            textView.text = "Error: ${e.message}"
            e.printStackTrace()
        }
    }

    private fun getSmsMessages(configuredSenders: Set<String>): Map<String, List<String>> {
        val messages = mutableMapOf<String, MutableList<String>>()
        val uri = Uri.parse("content://sms")
        
        val cursor: Cursor? = contentResolver.query(
            uri,
            arrayOf("address", "body"),
            "address IN (${configuredSenders.joinToString(",") { "'$it'" }})",
            null,
            "date DESC"
        )
        
        cursor?.use {
            val addressColumn = it.getColumnIndex("address")
            val bodyColumn = it.getColumnIndex("body")
            
            while (it.moveToNext()) {
                val address = it.getString(addressColumn)
                val body = it.getString(bodyColumn)
                
                if (!address.isNullOrBlank() && !body.isNullOrBlank()) {
                    messages.getOrPut(address) { mutableListOf() }.add(body)
                }
            }
        }
        
        return messages
    }
}
