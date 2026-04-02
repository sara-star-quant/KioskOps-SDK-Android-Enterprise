package com.sarastarquant.kioskops.sdk.util

fun interface Clock {
  fun nowMs(): Long

  companion object {
    val SYSTEM: Clock = Clock { System.currentTimeMillis() }
  }
}
