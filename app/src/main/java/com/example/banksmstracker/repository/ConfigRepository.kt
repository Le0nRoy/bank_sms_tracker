package com.example.banksmstracker.repository

import com.example.banksmstracker.data.SmsConfig
import com.example.banksmstracker.serializer.ConfigLoader
import kotlinx.serialization.SerializationException
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.RuntimeException

object ConfigRepository {

    private var _config: SmsConfig? = null
    val config: SmsConfig
        get() = _config ?: throw IllegalStateException("Config not initialized")

    fun load(application: android.app.Application) {
        if (_config != null) return
        try {
            val json = application.assets.open("default_rules.json")
                .bufferedReader()
                .use { it.readText() }
            _config = ConfigLoader.load(json)
        } catch (e: FileNotFoundException) {
            val message = "Failed to find config file"
            android.util.Log.e("ConfigRepository", message, e)
            throw RuntimeException(message, e)
        } catch (e: SerializationException) {
            val message = "Failed to deserialize config"
            android.util.Log.e("ConfigRepository", message, e)
            throw RuntimeException(message, e)
        } catch (e: IOException) {
            val message = "Failed to open config"
            android.util.Log.e("ConfigRepository", message, e)
            throw RuntimeException(message, e)
        }
    }

    // Added for testing purposes
    internal fun reset() {
        _config = null
    }
}
