package com.sarastarquant.kioskops.sdk.transport

fun interface NonceProvider {
  fun nextNonce(): String
}
