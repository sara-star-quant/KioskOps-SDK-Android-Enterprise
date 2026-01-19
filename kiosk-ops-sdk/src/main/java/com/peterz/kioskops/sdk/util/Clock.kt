package com.peterz.kioskops.sdk.util

fun interface Clock {
  fun nowMs(): Long

  companion object {
    val SYSTEM: Clock = Clock { System.currentTimeMillis() }
  }
}
