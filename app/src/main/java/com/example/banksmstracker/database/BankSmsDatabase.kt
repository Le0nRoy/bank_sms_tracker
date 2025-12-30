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
        PaymentEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BankSmsDatabase : RoomDatabase() {

    abstract fun configDao(): ConfigDao
    abstract fun paymentDao(): PaymentDao

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

        fun getInstance(context: Context): BankSmsDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                BankSmsDatabase::class.java,
                "bank_sms_tracker.db"
            )
                .addMigrations(MIGRATION_1_2)
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
