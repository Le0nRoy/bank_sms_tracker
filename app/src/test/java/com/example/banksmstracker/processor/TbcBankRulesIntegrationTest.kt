package com.example.banksmstracker.processor

import com.example.banksmstracker.data.MessageProcessResult
import com.example.banksmstracker.serializer.ConfigLoader
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Integration tests validating the default TBC Bank SMS rules against real-world message examples.
 * Covers IGNORE and INCOME rule types that are not exercised via sms_tests.json.
 */
class TbcBankRulesIntegrationTest {

    private val config = ConfigLoader.load(
        javaClass.classLoader!!.getResourceAsStream("default_rules.json")!!.bufferedReader().readText()
    )
    private val processor = ConfigLoader.createPaymentProcessor(config)

    @Nested
    inner class IgnoreRules {

        @Test
        fun `declined transaction should be ignored`() {
            val message = "Transaction 19.00 GEL  was declined.\n\nReason: Not enough funds." +
                "\nMC GOLD (***'9664') \n13/01/2026\nGOOGLE *Telegram\nBalance: 10.67 GEL "
            val result = processor.getMessageResult(message, "TBC SMS")
            assertTrue(result is MessageProcessResult.Ignored)
        }

        @Test
        fun `OTP for money transfer should be ignored`() {
            val message = "<#> By entering code you confirm transfer to someone." +
                "\nPlease, make sure you're entering it on https://tbconline.ge or in mobilebank" +
                "\n8950\nUkq+nWD1jJ0"
            val result = processor.getMessageResult(message, "TBC SMS")
            assertTrue(result is MessageProcessResult.Ignored)
        }

        @Test
        fun `OTP for card payment confirmation should be ignored`() {
            val message = "Code: 914970\nWith the code, you can confirm a 212.00 GEL payment" +
                " with your card ***9664 at TKT.GE1"
            val result = processor.getMessageResult(message, "TBC SMS")
            assertTrue(result is MessageProcessResult.Ignored)
        }

        @Test
        fun `short hash OTP should be ignored`() {
            val message = "<#> Code:3135 Please check that you enter this code only on" +
                " https://tbconline.ge or in our mobile application Ukq+nWD1jJ0"
            val result = processor.getMessageResult(message, "TBC SMS")
            assertTrue(result is MessageProcessResult.Ignored)
        }

        @Test
        fun `generic OTP verification code should be ignored`() {
            val message = "Code: 4363\nPlease check that you enter this code on web address" +
                " https://tbconline.ge or in mobilebank\ncKFVduDh43e"
            val result = processor.getMessageResult(message, "TBC SMS")
            assertTrue(result is MessageProcessResult.Ignored)
        }

        @Test
        fun `currency conversion notification should be ignored`() {
            val message = "Conversion:\n75.05 USD\n200.00 GEL\nRate: 2.665\n12/01/2026"
            val result = processor.getMessageResult(message, "TBC SMS")
            assertTrue(result is MessageProcessResult.Ignored)
        }

        @Test
        fun `internal account transfer should be ignored`() {
            val message = "Transfer between Accounts: \n17.53 USD\n09/01/2026"
            val result = processor.getMessageResult(message, "TBC SMS")
            assertTrue(result is MessageProcessResult.Ignored)
        }
    }

    @Nested
    inner class IncomeRules {

        @Test
        fun `salary deposit to card should be parsed as income`() {
            val message = "Deposit money: 5485.00 USD\nMC GOLD (***9664)" +
                "\nLets Deel 05/01/2026 17:01:50\nBalance: 14599.73 GEL "
            val result = processor.getMessageResult(message, "TBC SMS")
            assertTrue(result is MessageProcessResult.IncomeResult)
            val income = (result as MessageProcessResult.IncomeResult).income
            assertEquals(5485.00, income.amount)
            assertEquals("USD", income.currency)
            assertEquals("Lets Deel", income.source)
            assertEquals("05/01/2026 17:01:50", income.timestamp)
        }

        @Test
        fun `cash deposit to account should be parsed as income`() {
            val message = "Deposit Money: 110.00 GEL\nCurrent\n12/01/2026\n"
            val result = processor.getMessageResult(message, "TBC SMS")
            assertTrue(result is MessageProcessResult.IncomeResult)
            val income = (result as MessageProcessResult.IncomeResult).income
            assertEquals(110.00, income.amount)
            assertEquals("GEL", income.currency)
            assertEquals("Current", income.source)
            assertEquals("12/01/2026", income.timestamp)
        }

        @Test
        fun `incoming money transfer should be parsed as income`() {
            val message = "Money Transfer:\n134.60 GEL\nMC GOLD\n12/01/2026\n"
            val result = processor.getMessageResult(message, "TBC SMS")
            assertTrue(result is MessageProcessResult.IncomeResult)
            val income = (result as MessageProcessResult.IncomeResult).income
            assertEquals(134.60, income.amount)
            assertEquals("GEL", income.currency)
            assertEquals("MC GOLD", income.source)
            assertEquals("12/01/2026", income.timestamp)
        }

        @Test
        fun `payment reversal should be parsed as income`() {
            val message = "Reversal:\n249.00 GEL\nMC GOLD (***9664)\nzoommer (pekini)" +
                "\n05/01/2026 18:43:33\nBalance: 269.48 GEL \n"
            val result = processor.getMessageResult(message, "TBC SMS")
            assertTrue(result is MessageProcessResult.IncomeResult)
            val income = (result as MessageProcessResult.IncomeResult).income
            assertEquals(249.00, income.amount)
            assertEquals("GEL", income.currency)
            assertEquals("zoommer (pekini)", income.source)
            assertEquals("05/01/2026 18:43:33", income.timestamp)
            assertEquals(269.48, income.balance)
        }
    }
}
