package dev.alllexey.itmowidgets.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** `docs/design/icons.tsv` is the one list of app icons; every `ic_*` drawable has exactly one row. */
class IconRegistryTest {

    private val root = listOf(File(".."), File("."))
        .first { File(it, REGISTRY).isFile }
    private val lines = File(root, REGISTRY).readLines().filter { it.isNotEmpty() }
    private val rows = lines.drop(1).map { line -> line.split('\t') }

    @Test
    fun `registry has the agreed columns`() {
        assertEquals(COLUMNS, lines.first().split('\t'))
        rows.forEach { row -> assertEquals(row.joinToString("\t"), COLUMNS.size, row.size) }
    }

    @Test
    fun `ids are unique, sorted and lower snake case`() {
        val ids = rows.map { it[ID] }
        assertEquals(ids.distinct(), ids)
        assertEquals(ids.sorted(), ids)
        ids.forEach { id -> assertTrue(id, SNAKE.matches(id)) }
    }

    @Test
    fun `rows and drawables match one to one`() {
        val drawables = File(root, "app/src/main/res/drawable")
            .listFiles { file -> file.name.startsWith("ic_") && !file.name.startsWith("ic_launcher_") }
            .orEmpty()
            .map { it.name }
            .sorted()
        assertEquals(rows.map { "ic_${it[ID]}.xml" }.sorted(), drawables)
    }

    @Test
    fun `kind decides symbol, fill and SF Symbol`() {
        rows.forEach { row ->
            val label = row[ID]
            assertTrue(label, row[KIND] in KINDS)
            assertTrue(label, row[FILL] == "0" || row[FILL] == "1")
            assertTrue(label, row[NOTE].isNotBlank())
            when (row[KIND]) {
                "shared" -> {
                    assertTrue(label, SNAKE.matches(row[SYMBOL]))
                    assertTrue(label, row[SF_SYMBOL] != "-" && row[SF_SYMBOL].isNotBlank())
                }
                "custom" -> assertTrue(label, row[SF_SYMBOL] != "-" && row[SF_SYMBOL].isNotBlank())
                "android" -> assertEquals(label, "-", row[SF_SYMBOL])
            }
        }
    }

    private companion object {
        const val REGISTRY = "docs/design/icons.tsv"
        val COLUMNS = listOf("id", "symbol", "fill", "kind", "sf_symbol", "note")
        const val ID = 0
        const val SYMBOL = 1
        const val FILL = 2
        const val KIND = 3
        const val SF_SYMBOL = 4
        const val NOTE = 5
        val KINDS = setOf("shared", "custom", "android")
        val SNAKE = Regex("[a-z][a-z0-9]*(_[a-z0-9]+)*")
    }
}
