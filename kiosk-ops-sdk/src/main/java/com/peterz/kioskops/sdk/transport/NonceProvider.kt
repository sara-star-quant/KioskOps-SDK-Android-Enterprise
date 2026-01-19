package com.peterz.kioskops.sdk.transport

fun interface NonceProvider {
  fun nextNonce(): String
}
