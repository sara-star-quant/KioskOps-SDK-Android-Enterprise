package com.peterz.kioskops.sdk.util

import java.security.MessageDigest
import java.util.Base64

object Hashing {
  fun sha256Base64Url(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(input.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
  }
}
