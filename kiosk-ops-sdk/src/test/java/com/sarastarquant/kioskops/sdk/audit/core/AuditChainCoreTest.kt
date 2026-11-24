/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.core

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test
import java.io.File

class AuditChainCoreTest {

  private val vectors: JsonObject = Json.parseToJsonElement(
    File("../kioskops-verify/vectors/golden_vectors.json").readText(),
  ).jsonObject

  private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

  private fun link(seq: Long, prevHash: String, format: AuditHashFormat = AuditHashFormat.V2): ChainLink {
    val id = "id-$seq"
    val ts = 1_000L + seq
    val name = "event_$seq"
    val content = AuditChainCore.content(format, id, ts, name, "{}", prevHash)
    return ChainLink(seq, id, ts, name, "{}", prevHash, AuditChainCore.hash(content), format.version)
  }

  private fun chain(n: Int): List<ChainLink> {
    val out = mutableListOf<ChainLink>()
    var prev = "GENESIS"
    for (seq in 1L..n) {
      out += link(seq, prev).also { prev = it.hash }
    }
    return out
  }

  @Test
  fun `v1 content matches legacy string`() {
    assertThat(AuditChainCore.content(AuditHashFormat.V1, "id", 42L, "a|b", "{}", "GENESIS"))
      .isEqualTo("id|42|a|b|{}|GENESIS")
  }

  @Test
  fun `v2 content length-prefixes utf8 bytes`() {
    assertThat(AuditChainCore.content(AuditHashFormat.V2, "id", 42L, "caf\u00e9", "", "P"))
      .isEqualTo("2:id2:425:caf\u00e90:1:P")
  }

  @Test
  fun `v2 content differs for name a|b versus name a with fields b|{}`() {
    val v1a = AuditChainCore.content(AuditHashFormat.V1, "id", 1L, "a|b", "{}", "GENESIS")
    val v1b = AuditChainCore.content(AuditHashFormat.V1, "id", 1L, "a", "b|{}", "GENESIS")
    assertThat(v1a).isEqualTo(v1b)

    val v2a = AuditChainCore.content(AuditHashFormat.V2, "id", 1L, "a|b", "{}", "GENESIS")
    val v2b = AuditChainCore.content(AuditHashFormat.V2, "id", 1L, "a", "b|{}", "GENESIS")
    assertThat(v2a).isNotEqualTo(v2b)
  }

  @Test
  fun `golden vectors reproduce stored hashes`() {
    for (format in listOf(AuditHashFormat.V1, AuditHashFormat.V2)) {
      val list = vectors.getValue(format.name).jsonArray
      assertThat(list.size).isAtLeast(6)
      for (element in list) {
        val v = element.jsonObject
        val content = AuditChainCore.content(
          format, v.str("id"), v.getValue("ts").jsonPrimitive.long, v.str("name"), v.str("fieldsJson"), v.str("prevHash"),
        )
        assertWithMessage("$format ${v.str("id")}").that(AuditChainCore.hash(content)).isEqualTo(v.str("hash"))
      }
    }
  }

  @Test
  fun `file trail golden vectors reproduce stored hashes`() {
    val list = vectors.getValue("FILE_V0").jsonArray
    assertThat(list.size).isAtLeast(6)
    for (element in list) {
      val v = element.jsonObject
      val fields = v.getValue("fields").jsonObject.mapValues { it.value.jsonPrimitive.content }
      val content = AuditChainCore.content(
        AuditHashFormat.FILE_V0, "", v.getValue("ts").jsonPrimitive.long, v.str("name"),
        fields.toSortedMap().toString(), v.str("prevHash"),
      )
      assertThat(AuditChainCore.hash(content)).isEqualTo(v.str("hash"))
    }
  }

  @Test
  fun `verifyPage accepts an intact chain`() {
    val links = chain(4)
    assertThat(AuditChainCore.verifyPage(links, "GENESIS"))
      .isEqualTo(PageResult.Ok(links.last().hash, 4L))
  }

  @Test
  fun `verifyPage accepts v1 links`() {
    val first = link(1, "GENESIS", AuditHashFormat.V1)
    val second = link(2, first.hash, AuditHashFormat.V2)
    assertThat(AuditChainCore.verifyPage(listOf(first, second), "GENESIS"))
      .isEqualTo(PageResult.Ok(second.hash, 2L))
  }

  @Test
  fun `verifyPage detects swapped order`() {
    val links = chain(3)
    val swapped = listOf(links[0], links[2], links[1])
    val result = AuditChainCore.verifyPage(swapped, "GENESIS")
    assertThat(result).isInstanceOf(PageResult.Broken::class.java)
    assertThat((result as PageResult.Broken).seq).isEqualTo(3L)
  }

  @Test
  fun `verifyPage detects hash mismatch`() {
    val links = chain(3).toMutableList()
    links[1] = links[1].copy(name = "tampered")
    val result = AuditChainCore.verifyPage(links, "GENESIS")
    assertThat(result).isEqualTo(PageResult.Broken(2L, "hash does not match content"))
  }

  @Test
  fun `verifyPage detects a wrong starting prevHash`() {
    val result = AuditChainCore.verifyPage(chain(2), "OTHER")
    assertThat(result).isInstanceOf(PageResult.Broken::class.java)
    assertThat((result as PageResult.Broken).seq).isEqualTo(1L)
  }

  @Test
  fun `verifyPage reports unknown format version`() {
    val links = chain(2).toMutableList()
    links[1] = links[1].copy(formatVersion = 99)
    assertThat(AuditChainCore.verifyPage(links, "GENESIS"))
      .isEqualTo(PageResult.Broken(2L, "unknown format version 99"))
  }

  @Test
  fun `file format is not a stored event format`() {
    assertThat(AuditHashFormat.forStoredVersion(AuditHashFormat.FILE_V0.version)).isNull()
  }

  @Test
  fun `core package has no android imports`() {
    val dir = File("../kioskops-verify/src/commonMain/kotlin/com/sarastarquant/kioskops/sdk/audit/core")
    val files = dir.listFiles { f -> f.extension == "kt" }.orEmpty()
    assertThat(files).isNotEmpty()
    for (f in files) {
      val bad = f.readLines().filter { it.startsWith("import android.") || it.startsWith("import androidx.") }
      assertWithMessage(f.name).that(bad).isEmpty()
    }
  }
}
