package com.example.banksmstracker.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.banksmstracker.R

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupButtons()
    }

    private fun setupButtons() {
        findViewById<android.widget.Button>(R.id.btnCategories).setOnClickListener {
            startActivity(Intent(this, CategoriesActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.btnSenders).setOnClickListener {
            startActivity(Intent(this, SendersActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.btnCheckSenders).setOnClickListener {
            startActivity(Intent(this, CheckSendersActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.btnApplyRules).setOnClickListener {
            startActivity(Intent(this, ApplyRulesActivity::class.java))
        }
    }
}
