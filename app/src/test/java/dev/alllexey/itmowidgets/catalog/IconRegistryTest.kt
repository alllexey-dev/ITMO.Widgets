package dev.alllexey.itmowidgets.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `docs/design/icons.tsv` is the one list of app icons; every `ic_*` drawable has exactly one row. `shared` and
 * `custom` icons live in `:shared:designsystem` `composeResources` (exported to `:app` as Android drawables),
 * `android` rows and the launcher icon stay in `:app`.
 *
 * `shared` icons are in the house format `scripts/icons-fetch.py` writes: Material Symbols Rounded, a 960 viewport
 * drawn at 24 dp, untinted (the use site tints them; CMP's vector parser reads neither `android:tint` nor theme
 * colours), FILL 1 only for the selected bottom navigation tab. `android` rows are system-tinted masks and
 * `custom` rows keep their own drawing, so the format checks skip them.
 */
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
    fun `android rows stay in app`() {
        assertEquals(fileNames(rows.filter { it[KIND] == "android" }), appIcons())
    }

    @Test
    fun `shared and custom rows live in designsystem composeResources`() {
        val files = File(root, SHARED_DRAWABLES).list().orEmpty().sorted()
        assertEquals(fileNames(rows.filter { it[KIND] != "android" }), files)
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

    @Test
    fun `shared icons are Material Symbols Rounded in the house format`() {
        rows.filter { it[KIND] == "shared" }.forEach { row ->
            val text = drawable(row).readText()
            val style = if (row[FILL] == "1") "fill1" else "default"
            val url = "$SYMBOLS_URL/${row[SYMBOL]}/$style/24px.svg"
            assertTrue(row[ID], text.contains("<!-- Material Symbols Rounded: $url -->"))
            assertTrue(row[ID], text.contains(HOUSE_VECTOR))
            assertTrue(row[ID], text.contains("""<group android:translateY="960">"""))
            assertEquals(row[ID], setOf("#FF000000"), FILL_COLOR.findAll(text).map { it.groupValues[1] }.toSet())
        }
    }

    @Test
    fun `app icons carry no baked tint or theme colour`() {
        rows.filter { it[KIND] != "android" }.forEach { row ->
            val text = drawable(row).readText()
            UNTINTED.forEach { forbidden -> assertTrue("${row[ID]}: $forbidden", forbidden !in text) }
        }
    }

    @Test
    fun `only the filled variants use FILL 1`() {
        rows.filter { it[KIND] == "shared" }.forEach { row ->
            assertEquals(row[ID], row[ID].endsWith(FILLED), row[FILL] == "1")
            if (row[ID].endsWith(FILLED)) {
                val outline = rows.single { it[ID] == row[ID].removeSuffix(FILLED) }
                assertEquals(row[ID], outline[SYMBOL], row[SYMBOL])
            }
        }
    }

    @Test
    fun `bottom navigation fills the selected tab only`() {
        val items = NAV_ITEM.findAll(File(root, BOTTOM_NAV).readText()).map { it.groupValues[1] }.toList()
        assertEquals(5, items.size)
        val filled = items.map { selector ->
            assertTrue(selector, selector.startsWith("nav_"))
            val states = NAV_STATE.findAll(File(root, "$APP_DRAWABLES/$selector.xml").readText())
                .map { it.groupValues[1] to it.groupValues[2] }
                .toList()
            val checked = states.single { it.second.isNotEmpty() }
            val default = states.single { it.second.isEmpty() }
            assertEquals(selector, """ android:state_checked="true"""", checked.second)
            assertEquals(selector, "${default.first}$FILLED", checked.first)
            checked.first.removePrefix("ic_")
        }
        val filledRows = rows.filter { it[KIND] == "shared" && it[FILL] == "1" }.map { it[ID] }
        assertEquals(filledRows.sorted(), filled.sorted())
    }

    private fun drawable(row: List<String>) =
        File(root, "${if (row[KIND] == "android") APP_DRAWABLES else SHARED_DRAWABLES}/ic_${row[ID]}.xml")

    private fun fileNames(rows: List<List<String>>) = rows.map { "ic_${it[ID]}.xml" }.sorted()

    private fun appIcons() = File(root, APP_DRAWABLES)
        .listFiles { file -> file.name.startsWith("ic_") && !file.name.startsWith("ic_launcher_") }
        .orEmpty()
        .map { it.name }
        .sorted()

    private companion object {
        const val REGISTRY = "docs/design/icons.tsv"
        const val APP_DRAWABLES = "app/src/main/res/drawable"
        const val SHARED_DRAWABLES = "shared/designsystem/src/commonMain/composeResources/drawable"
        const val BOTTOM_NAV = "app/src/main/res/menu/bottom_nav.xml"
        const val SYMBOLS_URL = "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded"
        const val HOUSE_VECTOR = """android:width="24dp" android:height="24dp" """ +
            """android:viewportWidth="960" android:viewportHeight="960">"""
        const val FILLED = "_filled"
        val UNTINTED = listOf("android:tint", "?attr", "?color", "@android:color", "@color/")
        val FILL_COLOR = Regex("""android:fillColor="([^"]*)"""")
        val NAV_ITEM = Regex("""android:icon="@drawable/(\w+)"""")
        val NAV_STATE = Regex("""<item android:drawable="@drawable/(\w+)"((?: android:state_\w+="\w+")*) />""")
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
