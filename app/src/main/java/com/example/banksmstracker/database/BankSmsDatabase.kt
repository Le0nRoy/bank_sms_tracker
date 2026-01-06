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
        IgnoreRuleEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class BankSmsDatabase : RoomDatabase() {

    abstract fun configDao(): ConfigDao
    abstract fun paymentDao(): PaymentDao
    abstract fun ignoreRuleDao(): IgnoreRuleDao

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

        fun getInstance(context: Context): BankSmsDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                BankSmsDatabase::class.java,
                "bank_sms_tracker.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
