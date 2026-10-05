package dev.alllexey.itmowidgets.buildlogic.strings

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidStringIdsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val golden = File(requireNotNull(javaClass.getResource("/stringIds/golden/ExportedStringIds.kt")).toURI())
    private val exported = File(requireNotNull(javaClass.getResource("/export/golden")).toURI())

    @Test
    fun `the table of an exported file matches the golden copy`() {
        val keys = AndroidStringIds.keys(exported)

        assertEquals(AndroidStringIds.Keys(strings = listOf("common_retry"), plurals = listOf("common_days")), keys)
        assertEquals(golden.readText(), AndroidStringIds.render(APP, "$APP.core.ui", keys))
    }

    @Test
    fun `keys of every values directory are merged, distinct and sorted`() {
        val res = temp.newFolder()
        write(res, "values/a.xml", """<resources><string name="b">B</string><string name="a">A</string></resources>""")
        write(res, "values-night/a.xml", """<resources><string name="a">A</string></resources>""")
        write(res, "drawable/ic_x.xml", """<vector/>""")

        assertEquals(AndroidStringIds.Keys(strings = listOf("a", "b"), plurals = emptyList()), AndroidStringIds.keys(res))
    }

    private fun write(root: File, path: String, text: String) {
        val file = File(root, path)
        file.parentFile.mkdirs()
        file.writeText(text)
    }

    private companion object {
        const val APP = "dev.alllexey.itmowidgets"
    }
}
