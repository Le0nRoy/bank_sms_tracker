package com.example.banksmstracker

import com.example.banksmstracker.repository.ConfigRepository

class BankSmsTrackerApp : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        ConfigRepository.load(this)
    }
}
