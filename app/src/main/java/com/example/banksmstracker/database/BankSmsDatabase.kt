package com.example.banksmstracker.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        CategoryMerchantEntity::class,
        SenderEntity::class,
        SenderAddressEntity::class,
        SenderRuleEntity::class,
        PaymentEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BankSmsDatabase : RoomDatabase() {

    abstract fun configDao(): ConfigDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        @Volatile
        private var INSTANCE: BankSmsDatabase? = null

        fun getInstance(context: Context): BankSmsDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                BankSmsDatabase::class.java,
                "bank_sms_tracker.db"
            ).build().also { INSTANCE = it }
        }
    }
}
