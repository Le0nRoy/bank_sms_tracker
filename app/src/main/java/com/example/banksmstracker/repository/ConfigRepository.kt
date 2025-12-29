package com.example.banksmstracker.repository

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.PaymentRegexRule
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.data.SmsConfig
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.database.CategoryEntity
import com.example.banksmstracker.database.CategoryMerchantEntity
import com.example.banksmstracker.database.ConfigDao
import com.example.banksmstracker.database.SenderAddressEntity
import com.example.banksmstracker.database.SenderEntity
import com.example.banksmstracker.database.SenderRuleEntity
import com.example.banksmstracker.database.toDomainCategories
import com.example.banksmstracker.database.toDomainSenders
import com.example.banksmstracker.serializer.ConfigLoader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ConfigRepository {

    private var _config: SmsConfig? = null
    private lateinit var database: BankSmsDatabase
    private lateinit var configDao: ConfigDao
    private lateinit var paymentRepository: PaymentRepository
    private var paymentProcessor: com.example.banksmstracker.processor.PaymentProcessor? = null

    val config: SmsConfig
        get() = _config ?: throw IllegalStateException("Config not initialized")

    fun load(application: Application) {
        if (_config != null) return
        database = BankSmsDatabase.getInstance(application)
        configDao = database.configDao()
        paymentRepository = RoomPaymentRepository(database.paymentDao())

        runBlocking {
            if (isConfigEmpty()) {
                seedFromAssets(application)
            }
            refreshConfigInternal()
        }
    }

    suspend fun getCategories(): List<Category> = withContext(Dispatchers.IO) {
        refreshConfigInternal()
        config.categories.map { it.copy(merchants = it.merchants.toMutableList()) }
    }

    suspend fun getSenders(): List<Sender> = withContext(Dispatchers.IO) {
        refreshConfigInternal()
        config.senders.map { sender ->
            sender.copy(
                addresses = sender.addresses.toMutableList(),
                rules = sender.rules.map { PaymentRegexRule(id = it.id, regex = it.regex) }.toMutableList()
            )
        }
    }

    suspend fun addCategory(): Category = withContext(Dispatchers.IO) {
        val id = configDao.insertCategory(CategoryEntity(name = ""))
        refreshConfigInternal()
        config.categories.first { it.id == id }
    }

    suspend fun updateCategory(category: Category) = withContext(Dispatchers.IO) {
        val categoryId = category.id ?: error("Category must have id")
        database.withTransaction {
            configDao.updateCategory(CategoryEntity(categoryId, category.name))
            configDao.deleteMerchantsForCategory(categoryId)
            category.merchants
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { merchant ->
                    configDao.insertMerchant(
                        CategoryMerchantEntity(categoryId = categoryId, name = merchant)
                    )
                }
        }
        refreshConfigInternal()
    }

    suspend fun addSender(): Sender = withContext(Dispatchers.IO) {
        val senderId = configDao.insertSender(SenderEntity(name = ""))
        refreshConfigInternal()
        config.senders.first { it.id == senderId }
    }

    suspend fun updateSender(sender: Sender) = withContext(Dispatchers.IO) {
        val senderId = sender.id ?: error("Sender must have id")
        database.withTransaction {
            configDao.updateSender(SenderEntity(senderId, sender.name))
            configDao.deleteAddressesForSender(senderId)
            sender.addresses
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { address ->
                    configDao.insertAddress(
                        SenderAddressEntity(senderId = senderId, address = address)
                    )
                }
            configDao.deleteRulesForSender(senderId)
            sender.rules
                .map { it.regex.trim() }
                .filter { it.isNotEmpty() }
                .forEach { regex ->
                    configDao.insertRule(
                        SenderRuleEntity(senderId = senderId, regex = regex)
                    )
                }
        }
        refreshConfigInternal()
    }

    fun getPaymentProcessor(): com.example.banksmstracker.processor.PaymentProcessor =
        paymentProcessor ?: com.example.banksmstracker.processor.PaymentProcessor(
            senders = config.senders,
            categories = config.categories,
            paymentRepository = paymentRepository
        ).also { paymentProcessor = it }

    suspend fun exportConfigJson(pretty: Boolean = true): String = withContext(Dispatchers.IO) {
        refreshConfigInternal()
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = pretty
        }
        json.encodeToString(config)
    }

    suspend fun shareConfigFile(context: Context, fileName: String = "sms_config.json"): Pair<File, Uri> {
        val json = exportConfigJson()
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, fileName).also { it.writeText(json) }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return file to uri
    }

    private suspend fun refreshConfigInternal() = withContext(Dispatchers.IO) {
        val categories = configDao.getCategories().toDomainCategories()
        val senders = configDao.getSenders().toDomainSenders()
        _config = SmsConfig(
            senders = senders.toMutableList(),
            categories = categories.toMutableList()
        )
        paymentProcessor = com.example.banksmstracker.processor.PaymentProcessor(
            senders = config.senders,
            categories = config.categories,
            paymentRepository = paymentRepository
        )
    }

    private suspend fun isConfigEmpty(): Boolean = withContext(Dispatchers.IO) {
        configDao.getCategoriesCount() == 0 && configDao.getSendersCount() == 0
    }

    private suspend fun seedFromAssets(application: Application) {
        try {
            val json = withContext(Dispatchers.IO) {
                application.assets.open("default_rules.json")
                    .bufferedReader()
                    .use { it.readText() }
            }
            val parsedConfig = ConfigLoader.load(json)
            persistConfig(parsedConfig)
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

    private suspend fun persistConfig(config: SmsConfig) = withContext(Dispatchers.IO) {
        database.withTransaction {
            config.senders.forEach { sender ->
                val senderId = configDao.insertSender(SenderEntity(name = sender.name))
                sender.addresses
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { address ->
                        configDao.insertAddress(
                            SenderAddressEntity(senderId = senderId, address = address)
                        )
                    }
                sender.rules
                    .map { it.regex.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { regex ->
                        configDao.insertRule(
                            SenderRuleEntity(senderId = senderId, regex = regex)
                        )
                    }
            }
            config.categories.forEach { category ->
                val categoryId = configDao.insertCategory(CategoryEntity(name = category.name))
                category.merchants
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { merchant ->
                        configDao.insertMerchant(
                            CategoryMerchantEntity(categoryId = categoryId, name = merchant)
                        )
                    }
            }
        }
    }

    internal fun reset() {
        _config = null
        paymentProcessor = null
    }
}
