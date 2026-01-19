package com.peterz.kioskops.sdk.queue

import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.crypto.SoftwareAesGcmCryptoProvider
import com.peterz.kioskops.sdk.crypto.NoopCryptoProvider
import org.junit.Test

class PayloadCodecTest {
  @Test fun plainRoundTrip() {
    val json = "{" + "\"x\":\"y\"" + "}" 
    val enc = PayloadCodec.encodeJson(json, encrypt = false, crypto = NoopCryptoProvider)
    assertThat(enc.encoding).isEqualTo(PayloadCodec.ENCODING_PLAIN_UTF8)
    assertThat(PayloadCodec.decodeToJson(enc.blob, enc.encoding, NoopCryptoProvider)).isEqualTo(json)
  }

  @Test fun encryptedRoundTrip() {
    val json = "{" + "\"scan\":\"12345\"" + "}" 
    val crypto = SoftwareAesGcmCryptoProvider()
    val enc = PayloadCodec.encodeJson(json, encrypt = true, crypto = crypto)
    assertThat(enc.encoding).isEqualTo(PayloadCodec.ENCODING_AESGCM_V1)
    assertThat(PayloadCodec.decodeToJson(enc.blob, enc.encoding, crypto)).isEqualTo(json)
  }
}
