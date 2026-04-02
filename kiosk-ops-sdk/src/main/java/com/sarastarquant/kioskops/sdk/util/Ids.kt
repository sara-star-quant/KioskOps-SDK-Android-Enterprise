package com.sarastarquant.kioskops.sdk.util

import java.util.UUID

object Ids {
  fun uuid(): String = UUID.randomUUID().toString()
}
