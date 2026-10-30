package com.megasmsrouter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.megasmsrouter.data.dao.*
import com.megasmsrouter.data.entities.*

@Database(
    entities = [
        ApiDestinationEntity::class,
        RoutingRuleEntity::class,
        RuleDestinationCrossRef::class,
        RequestHistoryEntity::class,
        PendingDeliveryEntity::class,
        ProcessedMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun destinationDao(): DestinationDao
    abstract fun ruleDao(): RuleDao
    abstract fun historyDao(): HistoryDao
    abstract fun pendingDao(): PendingDao
    abstract fun processedDao(): ProcessedDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "msr.db")
                .build().also { instance = it }
        }
    }
}
