package dev.alllexey.itmowidgets.testkit.screenshot

import dev.alllexey.itmowidgets.designsystem.preview.PreviewAppearance
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaselineInventoryTest {

    private val dir: File = Files.createTempDirectory("baselines").toFile()
    private val directory = BaselineDirectory(dir)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `a file name splits into base and appearance`() {
        val baseline = BaselineDirectory.parse("SportCard_waiting_green-narrow.png")

        assertEquals("SportCard_waiting", baseline.base)
        assertEquals(PreviewAppearance.GreenNarrow, baseline.appearance)
        assertNull(BaselineDirectory.parse("ProbeSwatch.png").base)
        assertNull(BaselineDirectory.parse("_light.png").base)
    }

    @Test
    fun `light and dark by default, all four when full, and every recorded appearance`() {
        touch("Card_dark-narrow.png")

        assertEquals(PreviewAppearance.Default, directory.appearances("Other", full = false))
        assertEquals(PreviewAppearance.All, directory.appearances("Other", full = true))
        assertEquals(
            listOf(PreviewAppearance.Light, PreviewAppearance.Dark, PreviewAppearance.DarkNarrow),
            directory.appearances("Card", full = false),
        )
    }

    @Test
    fun `a listed reference without its preview is pending, not stale`() {
        touch("Card_light.png", "SportScreen_content_light.png", "SportScreen_content_dark.png")
        directory.addReference("SportScreen_content")

        val inventory = BaselineInventory(previewBases = setOf("Card"), directory)

        assertEquals(
            listOf("SportScreen_content_dark.png", "SportScreen_content_light.png"),
            inventory.pendingReferences,
        )
        assertEquals(emptyList(), inventory.problems)
        assertTrue("reference (pending port): SportScreen_content_light.png" in inventory.report())
    }

    @Test
    fun `a baseline without preview or reference is stale`() {
        touch("Card_light.png", "Removed_light.png", "ProbeSwatch.png")

        val inventory = BaselineInventory(previewBases = setOf("Card"), directory)

        assertEquals(listOf("ProbeSwatch.png", "Removed_light.png"), inventory.stale)
        assertEquals(2, inventory.problems.size)
    }

    @Test
    fun `a reference still listed after its port fails`() {
        touch("SportScreen_content_light.png")
        directory.addReference("SportScreen_content")

        val inventory = BaselineInventory(previewBases = setOf("SportScreen_content"), directory)

        assertEquals(listOf("SportScreen_content"), inventory.portedReferences)
        assertEquals(emptyList(), inventory.pendingReferences)
        assertTrue(inventory.problems.single().startsWith("reference SportScreen_content still listed after its port"))
    }

    @Test
    fun `a listed reference without a baseline fails`() {
        directory.addReference("SportScreen_content")

        val inventory = BaselineInventory(previewBases = emptySet(), directory)

        assertEquals(listOf("SportScreen_content"), inventory.missingReferences)
        assertEquals(1, inventory.problems.size)
    }

    @Test
    fun `the reference index stays sorted and keeps its header`() {
        directory.addReference("Zeta")
        directory.addReference("Alpha")
        directory.addReference("Zeta")

        assertEquals(setOf("Alpha", "Zeta"), directory.references())
        val lines = File(dir, BaselineDirectory.REFERENCES).readLines()
        assertTrue(lines.first().startsWith("#"))
        assertEquals(listOf("Alpha", "Zeta"), lines.filterNot { it.startsWith("#") })
    }

    private fun touch(vararg names: String) = names.forEach { File(dir, it).writeBytes(byteArrayOf(0)) }
}
