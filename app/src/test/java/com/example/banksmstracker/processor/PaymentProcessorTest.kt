package com.example.banksmstracker.processor

import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.serializer.ConfigLoader
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@Serializable
data class SmsTestCase(val id: String, val rawMessage: String, val address: String, val expected: Payment)

object SmsTestLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadTests(jsonString: String): List<SmsTestCase> =
        json.decodeFromString(ListSerializer(SmsTestCase.serializer()), jsonString)
}

class PaymentParserTest {

    private val configJson = loadAssetFile("default_rules.json")
    private val config = ConfigLoader.load(configJson)
    private val processor = ConfigLoader.createPaymentProcessor(config)

    companion object {
        @JvmStatic
        fun smsTestCases(): List<SmsTestCase> {
            val testDataJson = loadTestResource("sms_tests.json")
            return SmsTestLoader.loadTests(testDataJson)
        }

        private fun loadTestResource(filename: String): String = javaClass.classLoader
            ?.getResourceAsStream(filename)
            ?.bufferedReader()
            ?.readText()!!
    }

    private fun loadAssetFile(filename: String): String =
        javaClass.classLoader?.getResourceAsStream(filename)?.bufferedReader()?.readText()!!

    @ParameterizedTest(name = "Parsing SMS case {index}: {0}")
    @MethodSource("smsTestCases")
    fun testPaymentParsing(testCase: SmsTestCase) {
        val payment = processor.getPaymentFromMessage(testCase.rawMessage, testCase.address)

        assertNotNull(payment, "Parsing failed for case ${testCase.id}")

        assertEquals(testCase.expected.amount, payment.amount, "Amount mismatch in case ${testCase.id}")
        assertEquals(testCase.expected.currency, payment.currency, "Currency mismatch in case ${testCase.id}")
        assertEquals(testCase.expected.card, payment.card, "Card mismatch in case ${testCase.id}")
        assertEquals(testCase.expected.merchant, payment.merchant, "Merchant mismatch in case ${testCase.id}")
        assertEquals(testCase.expected.timestamp, payment.timestamp, "Timestamp mismatch in case ${testCase.id}")
        assertEquals(testCase.expected.balance, payment.balance, "Balance mismatch in case ${testCase.id}")
        assertEquals(null, payment.categoryId, "Category should be null at parsing in case ${testCase.id}")
    }

    @Test
    fun testParseInvalidMessage() {
        val message = "Some unrelated SMS text"
        val address = "some address"

        assertFailsWith<UnparsedMessageException> {
            processor.getPaymentFromMessage(message, address)
        }
    }

    @ParameterizedTest(name = "Categorizing payment case {index}: {0}")
    @MethodSource("smsTestCases")
    fun testCategoryAssignment(testCase: SmsTestCase) {
        val processedPayment = processor.processMessage(testCase.rawMessage, testCase.address)
        assertNotNull(processedPayment, "Parsing failed for case ${testCase.id}")

        // Verify the payment was stored
        val allPayments = processor.paymentRepository.getAllPayments()
        assertFalse(allPayments.isEmpty())
        assertTrue(allPayments.any { it.merchant == testCase.expected.merchant })

        // Check if category was assigned correctly
        val uncategorizedPayments = processor.paymentRepository.getUncategorizedPayments()
        if (testCase.expected.categoryId != null) {
            assertEquals(
                testCase.expected.categoryId,
                processedPayment.categoryId,
                "Category mismatch in case ${testCase.id}"
            )
            assertTrue(
                uncategorizedPayments.isEmpty(),
                "Payment with category was returned by method, that returns uncategorized payments"
            )
        } else {
            // If no category expected, it should be null
            assertEquals(null, processedPayment.categoryId, "Category should be null in case ${testCase.id}")
            assertFalse(
                uncategorizedPayments.isEmpty(),
                "Payment without category was not returned by method, that returns uncategorized payments"
            )
        }
    }
}
