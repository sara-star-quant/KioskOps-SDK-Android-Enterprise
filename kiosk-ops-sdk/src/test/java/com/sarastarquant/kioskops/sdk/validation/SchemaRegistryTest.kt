package com.sarastarquant.kioskops.sdk.validation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SchemaRegistryTest {
  @Test fun `register and retrieve schema`() {
    val registry = SchemaRegistry()
    registry.register("SCAN", """{"type":"object"}""")
    assertThat(registry.getSchema("SCAN")).isEqualTo("""{"type":"object"}""")
  }

  @Test fun `listRegistered returns all event types`() {
    val registry = SchemaRegistry()
    registry.register("A", "{}")
    registry.register("B", "{}")
    assertThat(registry.listRegistered()).containsExactly("A", "B")
  }

  @Test fun `unregister removes schema`() {
    val registry = SchemaRegistry()
    registry.register("A", "{}")
    registry.unregister("A")
    assertThat(registry.getSchema("A")).isNull()
  }

  @Test fun `clear removes all schemas`() {
    val registry = SchemaRegistry()
    registry.register("A", "{}")
    registry.register("B", "{}")
    registry.clear()
    assertThat(registry.listRegistered()).isEmpty()
  }

  @Test fun `getSchema returns null for unregistered`() {
    val registry = SchemaRegistry()
    assertThat(registry.getSchema("MISSING")).isNull()
  }
}
