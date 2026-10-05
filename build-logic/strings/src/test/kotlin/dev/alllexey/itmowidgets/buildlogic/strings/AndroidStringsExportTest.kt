package dev.alllexey.itmowidgets.buildlogic.strings

import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidStringsExportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val fixtures = File(requireNotNull(javaClass.getResource("/export")).toURI())
    private val coreResources = File(fixtures, "shared/core/src/commonMain/composeResources")

    @Test
    fun `an exported file matches the golden copy`() {
        val generated = exportCore()

        assertEquals(tree(File(fixtures, "golden")), tree(generated))
    }

    @Test
    fun `a module export coexists with a same-named file in app`() {
        val merged = temp.newFolder("merged")
        AndroidStringsExport.merge(listOf(exportCore()), merged)
        val appValues = File(fixtures, "app/src/main/res/values")

        assertEquals(setOf("values/core_strings_common.xml"), tree(merged).keys)
        assertEquals(setOf("strings_common.xml"), appValues.list().orEmpty().toSet())
        assertEquals(
            File(fixtures, "golden/values/core_strings_common.xml").readText(),
            File(merged, "values/core_strings_common.xml").readText(),
        )
    }

    @Test
    fun `two modules exporting one path fail`() {
        val first = exportCore()
        val second = temp.newFolder("second")
        first.copyRecursively(second)

        assertThrows(GradleException::class.java) { AndroidStringsExport.merge(listOf(first, second), temp.newFolder()) }
    }

    @Test
    fun `a module name with a hyphen gives a valid resource file name`() {
        assertEquals(
            "values-night/feature_qr_strings_qr.xml",
            AndroidStringsExport.exportedPath("feature-qr", "values-night/strings_qr.xml"),
        )
    }

    @Test
    fun `only string files under values can be exported`() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidStringsExport.requireExportable("drawable/ic_qr.xml")
        }
    }

    @Test
    fun `drawables keep their resource names and get the header`() {
        val out = temp.newFolder()
        val paths = AndroidStringsExport.drawablePaths(coreResources)

        AndroidStringsExport.export("core", coreResources, CORE_PREFIX, paths, out)

        assertEquals(listOf("drawable/ic_sample.xml"), paths)
        assertEquals(
            AndroidStringsExport.render(
                "$CORE_PREFIX/drawable/ic_sample.xml",
                File(coreResources, "drawable/ic_sample.xml").readText(),
            ),
            File(out, "drawable/ic_sample.xml").readText(),
        )
    }

    @Test
    fun `exporting drawables from a module without any fails`() {
        assertThrows(GradleException::class.java) { AndroidStringsExport.drawablePaths(temp.newFolder()) }
    }

    @Test
    fun `a file without an XML declaration gets the header first`() {
        assertEquals(
            "<!-- Generated from a/strings_x.xml, do not edit. -->\n<resources/>\n",
            AndroidStringsExport.render("a/strings_x.xml", "<resources/>\n"),
        )
    }

    private fun exportCore(): File {
        val out = temp.newFolder()
        AndroidStringsExport.export(
            module = "core",
            resourcesDir = coreResources,
            sourcePrefix = CORE_PREFIX,
            paths = listOf("values/strings_common.xml"),
            outputDir = out,
        )
        return out
    }

    private fun tree(dir: File): Map<String, String> = dir.walkTopDown().filter { it.isFile }
        .associate { it.relativeTo(dir).invariantSeparatorsPath to it.readText() }

    private companion object {
        const val CORE_PREFIX = "shared/core/src/commonMain/composeResources"
    }
}
