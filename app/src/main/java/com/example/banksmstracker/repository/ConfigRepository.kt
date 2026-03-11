package com.example.banksmstracker.repository

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Rule
import com.example.banksmstracker.data.RuleType
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.data.SmsConfig
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.database.CategoryEntity
import com.example.banksmstracker.database.CategoryMerchantEntity
import com.example.banksmstracker.database.ConfigDao
import com.example.banksmstracker.database.RuleDao
import com.example.banksmstracker.database.RuleEntity
import com.example.banksmstracker.database.SenderAddressEntity
import com.example.banksmstracker.database.SenderEntity
import com.example.banksmstracker.database.toDomainCategories
import com.example.banksmstracker.database.toDomainSenders
import com.example.banksmstracker.database.toEntity
import com.example.banksmstracker.serializer.ConfigLoader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ConfigRepository {

    private val configMutex = Mutex()

    @Volatile
    private var _config: SmsConfig? = null
    private lateinit var database: BankSmsDatabase
    private lateinit var configDao: ConfigDao
    private lateinit var ruleDao: RuleDao
    private lateinit var paymentRepository: PaymentRepository

    @Volatile
    private var paymentProcessor: com.example.banksmstracker.processor.PaymentProcessor? = null

    val config: SmsConfig
        get() = _config ?: throw IllegalStateException("Config not initialized")

    fun load(application: Application, seedIfEmpty: Boolean = true) {
        if (_config != null) return
        database = BankSmsDatabase.getInstance(application)
        configDao = database.configDao()
        ruleDao = database.ruleDao()
        paymentRepository = RoomPaymentRepository(database.paymentDao())

        runBlocking {
            if (seedIfEmpty && isConfigEmpty()) {
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
                rules = sender.rules.map { it.copy() }.toMutableList(),
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
            configDao.updateCategory(
                CategoryEntity(id = categoryId, name = category.name, enabled = category.enabled)
            )
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
        recategorizeAllPayments()
    }

    suspend fun deleteCategory(categoryId: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            configDao.deleteMerchantsForCategory(categoryId)
            configDao.deleteCategory(CategoryEntity(id = categoryId, name = ""))
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
            configDao.updateSender(
                SenderEntity(id = senderId, name = sender.name, enabled = sender.enabled)
            )
            configDao.deleteAddressesForSender(senderId)
            sender.addresses
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { address ->
                    configDao.insertAddress(
                        SenderAddressEntity(senderId = senderId, address = address)
                    )
                }
            ruleDao.deleteRulesForSender(senderId)
            sender.rules
                .filter { it.pattern.trim().isNotEmpty() }
                .forEach { rule ->
                    ruleDao.insertRule(
                        RuleEntity(
                            senderId = senderId,
                            pattern = rule.pattern.trim(),
                            description = rule.description,
                            enabled = rule.enabled,
                            ruleType = rule.ruleType.value,
                        )
                    )
                }
        }
        refreshConfigInternal()
    }

    suspend fun deleteSender(senderId: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            configDao.deleteAddressesForSender(senderId)
            ruleDao.deleteRulesForSender(senderId)
            configDao.deleteSender(SenderEntity(id = senderId, name = ""))
        }
        refreshConfigInternal()
    }

    fun getPaymentProcessor(): com.example.banksmstracker.processor.PaymentProcessor =
        paymentProcessor ?: com.example.banksmstracker.processor.PaymentProcessor(
            senders = config.senders,
            categories = config.categories,
            paymentRepository = paymentRepository
        ).also { paymentProcessor = it }

    /**
     * Validation result for duplicate checks.
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class DuplicateName(val existingName: String) : ValidationResult()
        data class DuplicateAddress(val address: String, val existingSenderName: String) : ValidationResult()
    }

    /**
     * Validate sender before update - check for duplicate names and addresses.
     */
    suspend fun validateSender(sender: Sender): ValidationResult = withContext(Dispatchers.IO) {
        val senderId = sender.id
        val senderName = sender.name.trim()

        // Check for duplicate name (case-insensitive)
        if (senderName.isNotEmpty()) {
            val duplicateName = config.senders.find { s ->
                s.id != senderId && s.name.equals(senderName, ignoreCase = true)
            }
            if (duplicateName != null) {
                return@withContext ValidationResult.DuplicateName(duplicateName.name)
            }
        }

        // Check for duplicate addresses across senders
        val trimmedAddresses = sender.addresses.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        for (address in trimmedAddresses) {
            val otherSender = config.senders.find { s ->
                s.id != senderId && s.addresses.any { it.trim().lowercase() == address }
            }
            if (otherSender != null) {
                return@withContext ValidationResult.DuplicateAddress(address, otherSender.name)
            }
        }

        ValidationResult.Valid
    }

    /**
     * Validate category before update - check for duplicate names.
     */
    suspend fun validateCategory(category: Category): ValidationResult = withContext(Dispatchers.IO) {
        val categoryId = category.id
        val categoryName = category.name.trim()

        // Check for duplicate name (case-insensitive)
        if (categoryName.isNotEmpty()) {
            val duplicateName = config.categories.find { c ->
                c.id != categoryId && c.name.equals(categoryName, ignoreCase = true)
            }
            if (duplicateName != null) {
                return@withContext ValidationResult.DuplicateName(duplicateName.name)
            }
        }

        ValidationResult.Valid
    }

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

    /**
     * Import configuration from JSON string and merge with existing config.
     * New senders/categories are added. Existing ones (matched by name) have their
     * addresses/rules/merchants merged.
     */
    suspend fun importConfig(jsonString: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            val importedConfig = ConfigLoader.load(jsonString)
            mergeConfig(importedConfig)
        } catch (e: SerializationException) {
            ImportResult.Error("Failed to parse config: ${e.message}")
        } catch (e: Exception) {
            ImportResult.Error("Import failed: ${e.message}")
        }
    }

    /**
     * Import configuration from a file URI.
     */
    suspend fun importConfigFromUri(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            } ?: return@withContext ImportResult.Error("Could not read file")
            importConfig(jsonString)
        } catch (e: Exception) {
            ImportResult.Error("Failed to read file: ${e.message}")
        }
    }

    private suspend fun mergeConfig(importedConfig: SmsConfig): ImportResult {
        var sendersAdded = 0
        var sendersMerged = 0
        var categoriesAdded = 0
        var categoriesMerged = 0

        database.withTransaction {
            // Merge senders
            for (importedSender in importedConfig.senders) {
                val existingSender = config.senders.find {
                    it.name.equals(importedSender.name, ignoreCase = true)
                }

                if (existingSender != null) {
                    // Merge with existing sender
                    val mergedAddresses = (existingSender.addresses + importedSender.addresses)
                        .map { it.trim().lowercase() }
                        .distinct()
                        .toMutableList()

                    val existingPatterns = existingSender.rules.map { it.pattern.trim() }.toSet()
                    val newRules = importedSender.rules.filter {
                        it.pattern.trim() !in existingPatterns
                    }
                    val mergedRules = (existingSender.rules + newRules).toMutableList()

                    val mergedSender = existingSender.copy(
                        addresses = mergedAddresses,
                        rules = mergedRules,
                    )
                    updateSenderInternal(mergedSender)
                    sendersMerged++
                } else {
                    // Add new sender
                    val senderId = configDao.insertSender(
                        SenderEntity(name = importedSender.name, enabled = importedSender.enabled)
                    )
                    importedSender.addresses
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { address ->
                            configDao.insertAddress(
                                SenderAddressEntity(senderId = senderId, address = address)
                            )
                        }
                    importedSender.rules
                        .filter { it.pattern.trim().isNotEmpty() }
                        .forEach { rule ->
                            ruleDao.insertRule(
                                RuleEntity(
                                    senderId = senderId,
                                    pattern = rule.pattern.trim(),
                                    description = rule.description,
                                    enabled = rule.enabled,
                                    ruleType = rule.ruleType.value,
                                )
                            )
                        }
                    sendersAdded++
                }
            }

            // Merge categories
            for (importedCategory in importedConfig.categories) {
                val existingCategory = config.categories.find {
                    it.name.equals(importedCategory.name, ignoreCase = true)
                }

                if (existingCategory != null) {
                    // Merge merchants
                    val mergedMerchants = (existingCategory.merchants + importedCategory.merchants)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .toMutableList()

                    val mergedCategory = existingCategory.copy(merchants = mergedMerchants)
                    updateCategoryInternal(mergedCategory)
                    categoriesMerged++
                } else {
                    // Add new category
                    val categoryId = configDao.insertCategory(
                        CategoryEntity(name = importedCategory.name, enabled = importedCategory.enabled)
                    )
                    importedCategory.merchants
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { merchant ->
                            configDao.insertMerchant(
                                CategoryMerchantEntity(categoryId = categoryId, name = merchant)
                            )
                        }
                    categoriesAdded++
                }
            }
        }

        refreshConfigInternal()

        return ImportResult.Success(
            sendersAdded = sendersAdded,
            sendersMerged = sendersMerged,
            categoriesAdded = categoriesAdded,
            categoriesMerged = categoriesMerged
        )
    }

    private suspend fun updateSenderInternal(sender: Sender) {
        val senderId = sender.id ?: return
        configDao.updateSender(
            SenderEntity(id = senderId, name = sender.name, enabled = sender.enabled)
        )
        configDao.deleteAddressesForSender(senderId)
        sender.addresses
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { address ->
                configDao.insertAddress(
                    SenderAddressEntity(senderId = senderId, address = address)
                )
            }
        ruleDao.deleteRulesForSender(senderId)
        sender.rules
            .filter { it.pattern.trim().isNotEmpty() }
            .forEach { rule ->
                ruleDao.insertRule(
                    RuleEntity(
                        senderId = senderId,
                        pattern = rule.pattern.trim(),
                        description = rule.description,
                        enabled = rule.enabled,
                        ruleType = rule.ruleType.value,
                    )
                )
            }
    }

    private suspend fun updateCategoryInternal(category: Category) {
        val categoryId = category.id ?: return
        configDao.updateCategory(
            CategoryEntity(id = categoryId, name = category.name, enabled = category.enabled)
        )
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

    private suspend fun refreshConfigInternal() = withContext(Dispatchers.IO) {
        configMutex.withLock {
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
                val senderId = configDao.insertSender(
                    SenderEntity(name = sender.name, enabled = sender.enabled)
                )
                sender.addresses
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { address ->
                        configDao.insertAddress(
                            SenderAddressEntity(senderId = senderId, address = address)
                        )
                    }
                sender.rules
                    .filter { it.pattern.trim().isNotEmpty() }
                    .forEach { rule ->
                        ruleDao.insertRule(
                            RuleEntity(
                                senderId = senderId,
                                pattern = rule.pattern.trim(),
                                description = rule.description,
                                enabled = rule.enabled,
                                ruleType = rule.ruleType.value,
                            )
                        )
                    }
            }
            config.categories.forEach { category ->
                val categoryId = configDao.insertCategory(
                    CategoryEntity(name = category.name, enabled = category.enabled)
                )
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
        BankSmsDatabase.resetInstance()
    }

    /**
     * Clear all data from the database. Used for testing.
     * After calling this, you must call load() again to reinitialize.
     */
    internal suspend fun clearAllData() = withContext(Dispatchers.IO) {
        if (::database.isInitialized) {
            database.clearAllTables()
        }
        // Reset in-memory state - caller must call load() again
        _config = null
        paymentProcessor = null
    }

    /**
     * Check if config is loaded.
     */
    internal fun isLoaded(): Boolean = _config != null

    /**
     * Re-categorize all payments based on current category merchant mappings.
     * Returns the number of payments that were re-categorized.
     */
    suspend fun recategorizeAllPayments(): Int = withContext(Dispatchers.IO) {
        var count = 0
        val allPayments = paymentRepository.getAllPayments()

        for (payment in allPayments) {
            val merchant = payment.merchant ?: continue
            val newCategory = config.categories
                .filter { it.enabled }
                .firstOrNull { cat ->
                    cat.merchants.any { it.equals(merchant, ignoreCase = true) }
                }?.name

            if (newCategory != payment.categoryId) {
                val paymentId = payment.id ?: continue
                paymentRepository.updatePaymentCategory(paymentId, newCategory)
                count++
            }
        }
        count
    }
}
