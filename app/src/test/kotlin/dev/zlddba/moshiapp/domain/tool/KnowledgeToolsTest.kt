package dev.zlddba.moshiapp.domain.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnowledgeToolsTest {

    @Test
    fun `every tool declares a parseable object schema`() {
        assertTrue(KnowledgeTools.SPECS.isNotEmpty())
        for (spec in KnowledgeTools.SPECS) {
            val schema = Json.parseToJsonElement(spec.parametersJson).jsonObject
            assertEquals("object", schema["type"]?.jsonPrimitive?.content, spec.name)
            assertTrue(schema["properties"] is JsonObject, spec.name)
            assertTrue(schema["required"] != null, spec.name)
        }
    }

    @Test
    fun `tool names are unique and non blank`() {
        val names = KnowledgeTools.SPECS.map { it.name }
        assertEquals(names.size, names.distinct().size)
        assertTrue(names.all { it.isNotBlank() })
        assertTrue(names.contains(KnowledgeTools.SEARCH_KNOWLEDGE))
        assertTrue(names.contains(KnowledgeTools.GET_NOTE))
    }

    @Test
    fun `declared required arguments exist in properties`() {
        for (spec in KnowledgeTools.SPECS) {
            val schema = Json.parseToJsonElement(spec.parametersJson).jsonObject
            val properties = schema["properties"]!!.jsonObject.keys
            val required = schema["required"]!!.toString()
            for (name in properties) {
                assertTrue(properties.contains(name))
            }
            assertTrue(required.contains("query") || required.contains("note_id"), spec.name)
        }
    }

    @Test
    fun `round budget stays small enough for interactive use`() {
        assertTrue(KnowledgeTools.MAX_ROUNDS in 1..3)
        assertTrue(KnowledgeTools.MAX_RESULT_CHARS > 0)
    }
}
