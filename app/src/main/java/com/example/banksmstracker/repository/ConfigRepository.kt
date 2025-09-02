package com.example.banksmstracker.repository

import com.example.banksmstracker.data.SmsConfig
import com.example.banksmstracker.serializer.ConfigLoader

object ConfigRepository {

    private var _config: SmsConfig? = null
    val config: SmsConfig
        get() = _config ?: throw IllegalStateException("Config not initialized")

    fun load(application: android.app.Application) {
        if (_config != null) return
        val json = application.assets.open("default_rules.json")
            .bufferedReader()
            .use { it.readText() }
        _config = ConfigLoader.load(json)
    }
}
