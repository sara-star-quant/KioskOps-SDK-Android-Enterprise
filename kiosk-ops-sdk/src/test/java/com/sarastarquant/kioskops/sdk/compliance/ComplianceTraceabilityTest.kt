/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.compliance

import com.google.common.truth.Truth.assertThat
import io.github.classgraph.ClassGraph
import java.io.File
import org.junit.Test

/**
 * Generates and verifies the control-to-test traceability matrix from
 * [ComplianceControl] annotations.
 *
 * Run `./gradlew :kiosk-ops-sdk:testDebugUnitTest -Pcompliance.update` to
 * (re)write `docs/compliance/traceability.md`. In normal/CI runs this test only
 * reads, and fails if:
 *  - the committed matrix is out of date (an annotation was added/changed), or
 *  - an annotation cites a control that its framework mapping document does not
 *    list, or a framework has no mapping document.
 *
 * This mirrors the repo's generate-then-verify pattern (the .api binary-compat
 * file and the detekt baseline work the same way): the artifact is committed,
 * and the build fails on drift.
 */
class ComplianceTraceabilityTest {

  private data class Entry(val framework: String, val control: String, val test: String)

  private val frameworkDoc = mapOf(
    "NIST SP 800-171" to "NIST-SP-800-171-mapping.md",
  )

  private val docsDir = File(
    System.getProperty("compliance.docsDir")
      ?: error("compliance.docsDir system property not set (configured in build.gradle.kts)"),
  )
  private val matrixFile = File(docsDir, "traceability.md")

  @Test
  fun `traceability matrix is current and every cited control exists`() {
    val entries = scanAnnotations()
    assertThat(entries).isNotEmpty()

    val expected = render(entries)

    if (System.getProperty("compliance.update") == "true") {
      matrixFile.writeText(expected)
      return
    }

    // Every cited control must appear in its framework's mapping document.
    for ((framework, control) in entries.map { it.framework to it.control }.toSortedSet(compareBy({ it.first }, { it.second }))) {
      val docName = frameworkDoc[framework]
        ?: error("No mapping document registered for framework \"$framework\" in ComplianceTraceabilityTest.frameworkDoc")
      val doc = File(docsDir, docName)
      assertThat(doc.exists()).isTrue()
      assertThat(doc.readText()).contains(control)
    }

    val actual = if (matrixFile.exists()) matrixFile.readText() else ""
    assertThat(actual).isEqualTo(expected)
  }

  private fun scanAnnotations(): List<Entry> {
    val annName = ComplianceControl::class.java.name
    val containerName = "$annName\$Container"
    fun annotated(ci: io.github.classgraph.ClassInfo) =
      ci.hasAnnotation(annName) || ci.hasAnnotation(containerName) ||
        ci.methodInfo.any { it.hasAnnotation(annName) || it.hasAnnotation(containerName) }

    return ClassGraph()
      .enableClassInfo()
      .enableMethodInfo()
      .enableAnnotationInfo()
      .acceptPackages("com.sarastarquant.kioskops.sdk")
      .scan()
      .use { scan -> scan.allClasses.filter(::annotated).flatMap { entriesFor(it.loadClass()) } }
  }

  private fun entriesFor(cls: Class<*>): List<Entry> {
    val ann = ComplianceControl::class.java
    val classLevel = cls.getAnnotationsByType(ann).map { Entry(it.framework, it.control, cls.simpleName) }
    val methodLevel = cls.declaredMethods.flatMap { m ->
      m.getAnnotationsByType(ann).map { Entry(it.framework, it.control, "${cls.simpleName}.${m.name}") }
    }
    return classLevel + methodLevel
  }

  private fun render(entries: List<Entry>): String {
    val byControl = entries
      .groupBy { it.framework to it.control }
      .toSortedMap(compareBy({ it.first }, { it.second }))

    val sb = StringBuilder()
    sb.append("# Compliance control traceability\n\n")
    sb.append("Generated from `@ComplianceControl` annotations by `ComplianceTraceabilityTest`.\n")
    sb.append("Do not edit by hand; run `./gradlew :kiosk-ops-sdk:testDebugUnitTest -Pcompliance.update` to regenerate.\n\n")
    sb.append("| Framework | Control | Verified by |\n")
    sb.append("|-----------|---------|-------------|\n")
    for ((key, rows) in byControl) {
      val tests = rows.map { it.test }.toSortedSet().joinToString("; ")
      sb.append("| ${key.first} | ${key.second} | $tests |\n")
    }
    return sb.toString()
  }
}
