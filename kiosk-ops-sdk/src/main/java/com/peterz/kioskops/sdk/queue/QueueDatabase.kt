package com.peterz.kioskops.sdk.queue

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [QueueEventEntity::class],
  version = 2,
  exportSchema = true
)
abstract class QueueDatabase : RoomDatabase() {
  abstract fun queueDao(): QueueDao
}
