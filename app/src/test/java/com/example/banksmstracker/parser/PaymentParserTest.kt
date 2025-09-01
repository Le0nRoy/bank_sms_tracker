package com.example.banksmstracker.parser

import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.serializer.ConfigLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File

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
    companion object {
        @JvmStatic
        fun smsTestCases(): List<SmsTestCase> {
            val testDataJson = File("src/test/resources/sms_tests.json").readText()
            return SmsTestLoader.loadTests(testDataJson)
        }
    }
    
    private fun loadAssetFile(filename: String): String {
        return javaClass.classLoader?.getResourceAsStream(filename)?.bufferedReader()?.readText()!!
    }
    
    private fun loadTestResource(filename: String): String {
        return javaClass.classLoader?.getResourceAsStream(filename)?.bufferedReader()?.readText()!!
    }

    @ParameterizedTest(name = "Parsing SMS case {index}: {0}")
    @MethodSource("smsTestCases")
    fun testPaymentParsing(testCase: SmsTestCase) {
        val payment = parser.parse(testCase.rawMessage)

        assertNotNull(payment, "Parsing failed for case ${testCase.id}")

        assertEquals(testCase.expected.amount, payment!!.amount, "Amount mismatch in case ${testCase.id}")
        assertEquals(testCase.expected.currency, payment!!.currency, "Currency mismatch in case ${testCase.id}")
        assertEquals(testCase.expected.card, payment!!.card, "Card mismatch in case ${testCase.id}")
        assertEquals(testCase.expected.merchant, payment!!.merchant, "Merchant mismatch in case ${testCase.id}")
        assertEquals(testCase.expected.timestamp, payment!!.timestamp, "Timestamp mismatch in case ${testCase.id}")
        assertEquals(testCase.expected.balance, payment!!.balance, "Balance mismatch in case ${testCase.id}")
        assertEquals(null, payment!!.categoryId, "Category should be null at parsing in case ${testCase.id}")
    }

    @Test
    fun testParseInvalidMessage() {
        val message = "Some unrelated SMS text"

        assertFailsWith<UnparsedMessageException> {
            parser.parse(message)
        }
    }
    
    @ParameterizedTest(name = "Categorizing payment case {index}: {0}")
    @MethodSource("smsTestCases")
    fun testCategoryAssignment(testCase: SmsTestCase) {
        val rawPayment = parser.parse(testCase.rawMessage)
        assertNotNull(rawPayment, "Parsing failed for case ${testCase.id}")
        
        val processedPayment = processor.processPayment(rawPayment!!)
        
        // Verify the payment was stored
        val allPayments = processor.getUncategorizedPayments()
        assertTrue(allPayments.isEmpty() || allPayments.any { it.merchant == testCase.expected.merchant })
        
        // Check if category was assigned correctly
        if (testCase.expected.categoryId != null) {
            assertEquals(testCase.expected.categoryId, processedPayment.categoryId, "Category mismatch in case ${testCase.id}")
        } else {
            // If no category expected, it should be null
            assertEquals(null, processedPayment.categoryId, "Category should be null in case ${testCase.id}")
        }
    }
}