package com.example.banksmstracker.parser

import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.serializer.ConfigLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
data class SmsTestCase(
    val id: String,
    val rawMessage: String,
    val address: String,
    val expected: Payment
)

object SmsTestLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadTests(jsonString: String): List<SmsTestCase> =
        json.decodeFromString(ListSerializer(SmsTestCase.serializer()), jsonString)
}

class PaymentParserTest {

    private val configJson = loadAssetFile("default_rules.json")
    private val config = ConfigLoader.load(configJson)
    private val parser = ConfigLoader.createPaymentParser(config)
    private val processor = ConfigLoader.createPaymentProcessor(config)
    
    private val testDataJson = loadTestResource("sms_tests.json")
    private val testCases = SmsTestLoader.loadTests(testDataJson)
    
    private fun loadAssetFile(filename: String): String {
        // Try resource stream first, then fallback to file system
        return javaClass.classLoader?.getResourceAsStream(filename)?.bufferedReader()?.readText()
            ?: java.io.File("app/src/test/resources/$filename").readText()
    }
    
    private fun loadTestResource(filename: String): String {
        // Try resource stream first, then fallback to file system
        return javaClass.classLoader?.getResourceAsStream(filename)?.bufferedReader()?.readText()
            ?: java.io.File("app/src/test/resources/$filename").readText()
    }

    @Test
    fun testPaymentParsing() {
        for (testCase in testCases) {
            val payment = parser.parse(testCase.rawMessage)

            assertNotNull(payment)
            assertEquals(testCase.expected.amount, payment!!.amount)
            assertEquals(testCase.expected.currency, payment!!.currency)
            assertEquals(testCase.expected.card, payment!!.card)
            assertEquals(testCase.expected.merchant, payment!!.merchant)
            assertEquals(testCase.expected.timestamp, payment!!.timestamp)
            assertEquals(testCase.expected.balance, payment!!.balance)
            // Category is assigned later, not during message parsing
            assertEquals(null, payment!!.categoryId)
        }
    }

    @Test
    fun testParseInvalidMessage() {
        val message = "Some unrelated SMS text"

        assertFailsWith<UnparsedMessageException> {
            parser.parse(message)
        }
    }
    
    @Test
    fun testCategoryAssignment() {
        for (testCase in testCases) {
            val rawPayment = parser.parse(testCase.rawMessage)
            assertNotNull(rawPayment)
            
            val processedPayment = processor.processPayment(rawPayment!!)
            
            // Verify the payment was stored
            val allPayments = processor.getUncategorizedPayments()
            assertTrue(allPayments.isEmpty() || allPayments.any { it.merchant == testCase.expected.merchant })
            
            // Check if category was assigned correctly
            if (testCase.expected.categoryId != null) {
                assertEquals(testCase.expected.categoryId, processedPayment.categoryId)
            } else {
                // If no category expected, it should be null
                assertEquals(null, processedPayment.categoryId)
            }
        }
    }
}