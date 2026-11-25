/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The vectors every platform must reproduce byte for byte; runs on jvm, android host and iOS simulator. */
class GoldenVectorsTest {
  private val vectors: JsonObject = Json.parseToJsonElement(GOLDEN_VECTORS_JSON).jsonObject

  private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
  private fun JsonObject.num(key: String) = getValue(key).jsonPrimitive.long

  @Test
  fun v1AndV2VectorsReproduceStoredHashes() {
    for (format in listOf(AuditHashFormat.V1, AuditHashFormat.V2)) {
      val list = vectors.getValue(format.name).jsonArray
      assertTrue(list.size >= 6, "too few $format vectors")
      for (element in list) {
        val v = element.jsonObject
        val content = AuditChainCore.content(format, v.str("id"), v.num("ts"), v.str("name"), v.str("fieldsJson"), v.str("prevHash"))
        assertEquals(v.str("hash"), AuditChainCore.hash(content), "$format " + v.str("id"))
      }
    }
  }

  @Test
  fun fileTrailVectorsReproduceStoredHashes() {
    val list = vectors.getValue("FILE_V0").jsonArray
    assertTrue(list.size >= 6)
    for (element in list) {
      val v = element.jsonObject
      val fields = v.getValue("fields").jsonObject.entries.sortedBy { it.key }
        .joinToString(", ", "{", "}") { it.key + "=" + it.value.jsonPrimitive.content }
      val content = AuditChainCore.content(AuditHashFormat.FILE_V0, "", v.num("ts"), v.str("name"), fields, v.str("prevHash"))
      assertEquals(v.str("hash"), AuditChainCore.hash(content))
    }
  }

  @Test
  fun eraseVectorBridgesWithItsRunsAndBreaksWithoutThem() {
    val vector = vectors.getValue("ERASE_V2").jsonObject
    val kept = vector.getValue("keptSeqs").jsonArray.map { it.jsonPrimitive.long }.toSet()
    val links = vector.getValue("rows").jsonArray.map { it.jsonObject }.filter { it.num("seq") in kept }.map { v ->
      ChainLink(v.num("seq"), v.str("id"), v.num("ts"), v.str("name"), v.str("fieldsJson"), v.str("prevHash"), v.str("hash"), AuditHashFormat.V2.version)
    }
    val runs = vector.getValue("runs").jsonArray.map {
      val o = it.jsonObject
      ErasedRun(o.num("fromSeq"), o.num("toSeq"), o.str("startPrev"), o.str("endHash"))
    }
    assertIs<PageResult.Ok>(AuditChainCore.verifyPage(links, "GENESIS", runs))
    assertEquals(4L, assertIs<PageResult.Broken>(AuditChainCore.verifyPage(links, "GENESIS")).seq)
  }

  @Test
  fun hashHandlesEmptyAndMultibyteInput() {
    // SHA-256 of the empty string, base64url without padding.
    assertEquals("47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU", AuditChainCore.hash(""))
    assertEquals("2:id2:425:caf\u00e90:1:P", AuditChainCore.content(AuditHashFormat.V2, "id", 42L, "caf\u00e9", "", "P"))
  }
}
