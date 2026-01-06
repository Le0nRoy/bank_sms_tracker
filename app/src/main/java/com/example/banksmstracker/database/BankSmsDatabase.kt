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
        SenderRuleEntity::class,
        PaymentEntity::class,
        IgnoreRuleEntity::class,
        IncomeEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class BankSmsDatabase : RoomDatabase() {

    abstract fun configDao(): ConfigDao
    abstract fun paymentDao(): PaymentDao
    abstract fun ignoreRuleDao(): IgnoreRuleDao
    abstract fun incomeDao(): IncomeDao

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

        fun getInstance(context: Context): BankSmsDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                BankSmsDatabase::class.java,
                "bank_sms_tracker.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
