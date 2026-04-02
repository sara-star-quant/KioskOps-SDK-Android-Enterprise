package com.sarastarquant.kioskops.sdk.validation

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class JsonSchemaValidatorTest {
  private lateinit var registry: SchemaRegistry
  private lateinit var validator: JsonSchemaValidator

  @Before
  fun setUp() {
    registry = SchemaRegistry()
    validator = JsonSchemaValidator(registry)
  }

  @Test fun `valid payload passes type and required checks`() {
    registry.register("SCAN", """
      {"type":"object","required":["barcode"],"properties":{"barcode":{"type":"string"}}}
    """.trimIndent())
    val result = validator.validate("SCAN", """{"barcode":"ABC123"}""")
    assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
  }

  @Test fun `missing required field fails`() {
    registry.register("SCAN", """
      {"type":"object","required":["barcode"],"properties":{"barcode":{"type":"string"}}}
    """.trimIndent())
    val result = validator.validate("SCAN", """{"other":"value"}""")
    assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    val invalid = result as ValidationResult.Invalid
    assertThat(invalid.errors).hasSize(1)
    assertThat(invalid.errors[0]).contains("barcode")
  }

  @Test fun `wrong type fails`() {
    registry.register("T", """{"type":"object","properties":{"count":{"type":"integer"}}}""")
    val result = validator.validate("T", """{"count":"not_a_number"}""")
    assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
  }

  @Test fun `minLength violation fails`() {
    registry.register("T", """{"type":"object","properties":{"name":{"type":"string","minLength":3}}}""")
    val result = validator.validate("T", """{"name":"ab"}""")
    assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
  }

  @Test fun `maxLength violation fails`() {
    registry.register("T", """{"type":"object","properties":{"code":{"type":"string","maxLength":5}}}""")
    val result = validator.validate("T", """{"code":"toolong"}""")
    assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
  }

  @Test fun `pattern violation fails`() {
    registry.register("T", """{"type":"object","properties":{"zip":{"type":"string","pattern":"^\\d{5}$"}}}""")
    val result = validator.validate("T", """{"zip":"abcde"}""")
    assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
  }

  @Test fun `enum validation works`() {
    registry.register("T", """{"type":"object","properties":{"status":{"type":"string","enum":["ACTIVE","INACTIVE"]}}}""")
    assertThat(validator.validate("T", """{"status":"ACTIVE"}""")).isInstanceOf(ValidationResult.Valid::class.java)
    assertThat(validator.validate("T", """{"status":"UNKNOWN"}""")).isInstanceOf(ValidationResult.Invalid::class.java)
  }

  @Test fun `unregistered event type returns SchemaNotFound`() {
    val result = validator.validate("UNKNOWN", """{"x":1}""")
    assertThat(result).isInstanceOf(ValidationResult.SchemaNotFound::class.java)
  }

  @Test fun `array items validation works`() {
    registry.register("T", """
      {"type":"object","properties":{"tags":{"type":"array","items":{"type":"string"},"minItems":1}}}
    """.trimIndent())
    assertThat(validator.validate("T", """{"tags":["a","b"]}""")).isInstanceOf(ValidationResult.Valid::class.java)
    assertThat(validator.validate("T", """{"tags":[]}""")).isInstanceOf(ValidationResult.Invalid::class.java)
  }

  @Test fun `numeric minimum and maximum`() {
    registry.register("T", """{"type":"object","properties":{"age":{"type":"integer","minimum":0,"maximum":150}}}""")
    assertThat(validator.validate("T", """{"age":25}""")).isInstanceOf(ValidationResult.Valid::class.java)
    assertThat(validator.validate("T", """{"age":-1}""")).isInstanceOf(ValidationResult.Invalid::class.java)
    assertThat(validator.validate("T", """{"age":200}""")).isInstanceOf(ValidationResult.Invalid::class.java)
  }

  @Test fun `format email validation`() {
    registry.register("T", """{"type":"object","properties":{"email":{"type":"string","format":"email"}}}""")
    assertThat(validator.validate("T", """{"email":"user@example.com"}""")).isInstanceOf(ValidationResult.Valid::class.java)
    assertThat(validator.validate("T", """{"email":"not-an-email"}""")).isInstanceOf(ValidationResult.Invalid::class.java)
  }

  @Test fun `additionalProperties false rejects unknown fields`() {
    registry.register("T", """{"type":"object","properties":{"a":{"type":"string"}},"additionalProperties":false}""")
    assertThat(validator.validate("T", """{"a":"ok"}""")).isInstanceOf(ValidationResult.Valid::class.java)
    assertThat(validator.validate("T", """{"a":"ok","b":"extra"}""")).isInstanceOf(ValidationResult.Invalid::class.java)
  }
}
