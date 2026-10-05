package dev.alllexey.itmowidgets.designsystem.tokens

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `tokens/itmo-tokens.json` equals what the Kotlin tokens export. `exportDesignTokens` runs this test with
 * `designTokens.write=true` to rewrite the file instead.
 */
class DesignTokensExportTest {

    /** Gradle runs host tests in the module directory, an IDE may run them in the repository root. */
    private val file = listOf(File(PATH), File("shared/designsystem/$PATH")).firstOrNull { it.isFile } ?: File(PATH)

    @Test
    fun `itmo-tokens json is current`() {
        val exported = designTokensJson()
        if (System.getProperty("designTokens.write") == "true") {
            file.absoluteFile.parentFile?.mkdirs()
            file.writeText(exported)
            return
        }
        assertTrue("${file.path} is missing; $HINT", file.isFile)
        assertEquals("${file.path} is stale; $HINT", exported, file.readText())
    }

    @Test
    fun `the export carries the schema version and every section`() {
        val json = Json.parseToJsonElement(designTokensJson()).jsonObject
        assertEquals(SCHEMA_VERSION, json.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals(
            listOf("schemaVersion", "source", "generatedBy", "color", "shape", "spacing", "type", "motion"),
            json.keys.toList(),
        )
        val color = json.getValue("color").jsonObject
        listOf("light", "dark").forEach { mode ->
            assertEquals(48, color.getValue("scheme").jsonObject.getValue(mode).jsonObject.size)
            assertEquals(20, color.getValue("extended").jsonObject.getValue(mode).jsonObject.size)
        }
        val type = json.getValue("type").jsonObject
        assertEquals(15, type.getValue("roles").jsonObject.size)
        assertEquals(15, type.getValue("emphasized").jsonObject.size)
    }

    private companion object {
        const val PATH = "tokens/itmo-tokens.json"
        const val HINT = "run scripts/verify.sh run -- :shared:designsystem:exportDesignTokens and commit it"
    }
}
