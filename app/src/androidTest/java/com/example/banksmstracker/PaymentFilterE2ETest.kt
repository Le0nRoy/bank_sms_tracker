package com.example.banksmstracker

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.PaymentRegexRule
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.parser.SmsReceiver
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.RoomPaymentRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * E2E tests for payment filtering functionality.
 * Tests filtering payments by sender, date range, and combined filters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentFilterE2ETest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var paymentRepository: RoomPaymentRepository

    private fun buildSmsIntent(sender: String, body: String): Intent =
        Intent("android.provider.Telephony.SMS_RECEIVED").apply {
            putExtra(SmsReceiver.EXTRA_TEST_SENDER, sender)
            putExtra(SmsReceiver.EXTRA_TEST_BODY, body)
        }

    @BeforeEach
    fun setup() {
        ConfigRepository.reset()
        ConfigRepository.load(context.applicationContext as android.app.Application)

        runBlocking {
            ConfigRepository.clearAllData()
        }

        ConfigRepository.reset()
        ConfigRepository.load(context.applicationContext as android.app.Application, seedIfEmpty = false)

        // Set up multiple senders
        runBlocking {
            val sender1 = ConfigRepository.addSender()
            sender1.name = "Bank A"
            sender1.addresses = mutableListOf("BANK-A")
            sender1.rules = mutableListOf(
                PaymentRegexRule(
                    regex = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            )
            ConfigRepository.updateSender(sender1)

            val sender2 = ConfigRepository.addSender()
            sender2.name = "Bank B"
            sender2.addresses = mutableListOf("BANK-B")
            sender2.rules = mutableListOf(
                PaymentRegexRule(
                    regex = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            )
            ConfigRepository.updateSender(sender2)
        }

        paymentRepository = RoomPaymentRepository(
            BankSmsDatabase.getInstance(context).paymentDao()
        )
    }

    @Test
    fun `filterBySender_returnsOnlyPaymentsFromSelectedSender`() {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        // Send payments from different senders
        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK-A", "Payment 100.00 USD card 1111 StoreA at 20230901 bal 500.00")
        )
        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK-B", "Payment 200.00 USD card 2222 StoreB at 20230902 bal 400.00")
        )
        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK-A", "Payment 150.00 USD card 1111 StoreC at 20230903 bal 350.00")
        )

        // Filter by sender BANK-A
        val bankAPayments = runBlocking { paymentRepository.getPaymentsBySender("BANK-A") }

        assertEquals(2, bankAPayments.size)
        assertTrue(bankAPayments.all { it.senderAddress == "BANK-A" })
    }

    @Test
    fun `filterBySender_returnsEmptyForUnknownSender`() {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK-A", "Payment 100.00 USD card 1111 Store at 20230901 bal 500.00")
        )

        val unknownPayments = runBlocking { paymentRepository.getPaymentsBySender("UNKNOWN") }
        assertTrue(unknownPayments.isEmpty())
    }

    @Test
    fun `getDistinctSenders_returnsAllUniqueSenders`() {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK-A", "Payment 100.00 USD card 1111 Store1 at 20230901 bal 500.00")
        )
        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK-B", "Payment 200.00 USD card 2222 Store2 at 20230902 bal 400.00")
        )
        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK-A", "Payment 300.00 USD card 1111 Store3 at 20230903 bal 300.00")
        )

        val senders = runBlocking { paymentRepository.getDistinctSenderAddresses() }

        assertEquals(2, senders.size)
        assertTrue(senders.contains("BANK-A"))
        assertTrue(senders.contains("BANK-B"))
    }

    @Test
    fun `filterByDateRange_returnsPaymentsWithinRange`() {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        val beforeTime = System.currentTimeMillis()

        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK-A", "Payment 100.00 USD card 1111 Store at 20230901 bal 500.00")
        )
        Thread.sleep(100)
        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK-A", "Payment 200.00 USD card 2222 Store at 20230902 bal 400.00")
        )

        val afterTime = System.currentTimeMillis()

        val rangePayments = runBlocking {
            paymentRepository.getPaymentsByDateRange(beforeTime - 1000, afterTime + 1000)
        }

        assertEquals(2, rangePayments.size)
    }

    @Test
    fun `filterByDateRange_excludesPaymentsOutsideRange`() {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK-A", "Payment 100.00 USD card 1111 Store at 20230901 bal 500.00")
        )

        // Query future range
        val futureStart = System.currentTimeMillis() + 100000
        val futureEnd = futureStart + 100000
        val emptyPayments = runBlocking {
            paymentRepository.getPaymentsByDateRange(futureStart, futureEnd)
        }

        assertTrue(emptyPayments.isEmpty())
    }

    @Test
    fun `paymentsStoreSenderAddressCorrectly`() {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK-A", "Payment 100.00 USD card 1111 Store at 20230901 bal 500.00")
        )

        val payments = runBlocking { paymentRepository.getAllPayments() }
        assertEquals(1, payments.size)
        assertEquals("BANK-A", payments[0].senderAddress)
    }

    @Test
    fun `paymentsStoreReceivedAtTimestampCorrectly`() {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        val beforeTime = System.currentTimeMillis()
        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK-A", "Payment 100.00 USD card 1111 Store at 20230901 bal 500.00")
        )
        val afterTime = System.currentTimeMillis()

        val payments = runBlocking { paymentRepository.getAllPayments() }
        assertEquals(1, payments.size)

        val receivedAt = payments[0].receivedAt
        assertTrue(receivedAt != null)
        assertTrue(receivedAt!! >= beforeTime)
        assertTrue(receivedAt <= afterTime)
    }
}
