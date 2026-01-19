package com.peterz.kioskops.sdk.queue

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [QueueEventEntity::class],
  version = 2,
  exportSchema = true
)
abstract class QueueDb : RoomDatabase() {
  abstract fun dao(): QueueDao
}
