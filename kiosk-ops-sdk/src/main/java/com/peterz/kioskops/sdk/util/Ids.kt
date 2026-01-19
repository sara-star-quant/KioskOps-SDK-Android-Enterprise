package com.peterz.kioskops.sdk.util

import java.util.UUID

object Ids {
  fun uuid(): String = UUID.randomUUID().toString()
}
