package com.example.banksmstracker.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        CategoryMerchantEntity::class,
        SenderEntity::class,
        SenderAddressEntity::class,
        RuleEntity::class,
        PaymentEntity::class,
        IgnoreRuleEntity::class,
        IncomeEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class BankSmsDatabase : RoomDatabase() {

    abstract fun configDao(): ConfigDao
    abstract fun paymentDao(): PaymentDao
    abstract fun ignoreRuleDao(): IgnoreRuleDao
    abstract fun incomeDao(): IncomeDao
    abstract fun ruleDao(): RuleDao

    companion object {
        @Volatile
        private var INSTANCE: BankSmsDatabase? = null

        /**
         * Migration from version 1 to 2: Add enabled columns to categories, senders, and sender_rules.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE senders ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE sender_rules ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * Migration from version 2 to 3: Add senderAddress and receivedAt columns to payments.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payments ADD COLUMN senderAddress TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE payments ADD COLUMN receivedAt INTEGER DEFAULT NULL")
            }
        }

        /**
         * Migration from version 3 to 4: Add ruleId column to payments for cascade tracking.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payments ADD COLUMN ruleId INTEGER DEFAULT NULL")
            }
        }

        /**
         * Migration from version 4 to 5: Add ignore_rules table for spam filtering.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ignore_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        pattern TEXT NOT NULL,
                        description TEXT,
                        enabled INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migration from version 5 to 6: Add senderId to ignore_rules (attach to senders).
         * Recreates table to add foreign key constraint.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new table with senderId column
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ignore_rules_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        senderId INTEGER NOT NULL,
                        pattern TEXT NOT NULL,
                        description TEXT,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY (senderId) REFERENCES senders(id) ON DELETE CASCADE ON UPDATE CASCADE
                    )
                    """.trimIndent()
                )
                // Create index on senderId
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ignore_rules_senderId ON ignore_rules_new(senderId)")
                // Drop old table (ignore rules without sender will be lost)
                db.execSQL("DROP TABLE IF EXISTS ignore_rules")
                // Rename new table
                db.execSQL("ALTER TABLE ignore_rules_new RENAME TO ignore_rules")
            }
        }

        /**
         * Migration from version 6 to 7: Add incomes table for income tracking.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS incomes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amount REAL NOT NULL,
                        currency TEXT NOT NULL,
                        source TEXT,
                        timestamp TEXT,
                        balance REAL,
                        messageHash TEXT NOT NULL,
                        senderAddress TEXT,
                        receivedAt INTEGER,
                        ruleId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_incomes_messageHash ON incomes(messageHash)")
            }
        }

        /**
         * Migration from version 7 to 8: Unified rules table.
         * Consolidates sender_rules and ignore_rules into a single rules table with rule_type column.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Step 1: Create new unified rules table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        senderId INTEGER NOT NULL,
                        pattern TEXT NOT NULL,
                        description TEXT,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        ruleType TEXT NOT NULL DEFAULT 'payment',
                        FOREIGN KEY (senderId) REFERENCES senders(id) ON DELETE CASCADE ON UPDATE CASCADE
                    )
                    """.trimIndent()
                )

                // Step 2: Migrate payment rules (sender_rules -> rules)
                db.execSQL(
                    """
                    INSERT INTO rules (senderId, pattern, description, enabled, ruleType)
                    SELECT senderId, regex, NULL, enabled, 'payment'
                    FROM sender_rules
                    """.trimIndent()
                )

                // Step 3: Migrate ignore rules (ignore_rules -> rules)
                db.execSQL(
                    """
                    INSERT INTO rules (senderId, pattern, description, enabled, ruleType)
                    SELECT senderId, pattern, description, enabled, 'ignore'
                    FROM ignore_rules
                    """.trimIndent()
                )

                // Step 4: Create indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rules_senderId ON rules(senderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rules_ruleType ON rules(ruleType)")

                // Note: Old tables (sender_rules, ignore_rules) are kept for backward compatibility
                // They will be removed in a future migration after the transition is complete
            }
        }

        /**
         * Migration from version 8 to 9: Make payments.timestamp NOT NULL; remove receivedAt column.
         *
         * Steps:
         * 1. Fill any NULL timestamps using receivedAt as a formatted date fallback.
         * 2. Recreate the payments table without receivedAt and with timestamp NOT NULL.
         * 3. Copy data over (COALESCE to '01/01/1970' as last resort — should never be needed
         *    after Step 2, but guards against edge cases).
         * 4. Drop old table and rename the new one.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE payments
                    SET timestamp = strftime('%d/%m/%Y', datetime(receivedAt / 1000, 'unixepoch'))
                    WHERE timestamp IS NULL AND receivedAt IS NOT NULL
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE payments_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amount REAL NOT NULL,
                        currency TEXT NOT NULL,
                        card TEXT,
                        merchant TEXT,
                        timestamp TEXT NOT NULL,
                        balance REAL,
                        categoryName TEXT,
                        messageHash TEXT UNIQUE,
                        senderAddress TEXT,
                        ruleId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO payments_new
                        (id, amount, currency, card, merchant, timestamp, balance,
                         categoryName, messageHash, senderAddress, ruleId)
                    SELECT id, amount, currency, card, merchant,
                        COALESCE(timestamp, '01/01/1970'),
                        balance, categoryName, messageHash, senderAddress, ruleId
                    FROM payments
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE payments")
                db.execSQL("ALTER TABLE payments_new RENAME TO payments")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_payments_messageHash ON payments(messageHash)"
                )
            }
        }

        /**
         * Migration from version 9 to 10: Extend category_merchants table with displayName and
         * isRegex columns; rename 'name' column to 'pattern'.
         *
         * SQLite does not support column renaming via ALTER TABLE on older Android versions, so the
         * table is recreated. Existing merchants are migrated as exact-match patterns
         * (isRegex = 0, displayName = NULL).
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE category_merchants_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        pattern TEXT NOT NULL,
                        displayName TEXT,
                        isRegex INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (categoryId) REFERENCES categories(id)
                            ON DELETE CASCADE ON UPDATE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_category_merchants_new_categoryId " +
                        "ON category_merchants_new(categoryId)"
                )
                db.execSQL(
                    """
                    INSERT INTO category_merchants_new (id, categoryId, pattern)
                        SELECT id, categoryId, name FROM category_merchants
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE category_merchants")
                db.execSQL("ALTER TABLE category_merchants_new RENAME TO category_merchants")
            }
        }

        /**
         * Migration from version 10 to 11: Drop legacy sender_rules table.
         *
         * The sender_rules table was migrated to the unified rules table in migration 7→8 and kept
         * "for backward compatibility". No code paths write to sender_rules any longer — all rule
         * inserts go through RuleDao which targets the rules table. Dropping the table removes the
         * dead weight and makes the schema match Room's entity list.
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS sender_rules")
            }
        }

        fun getInstance(context: Context): BankSmsDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                BankSmsDatabase::class.java,
                "bank_sms_tracker.db"
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11
                )
                .build()
                .also { INSTANCE = it }
        }

        /**
         * Close and clear the database instance. Used for testing.
         */
        internal fun resetInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
