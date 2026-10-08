package dev.alllexey.itmowidgets.architecture

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Source-level contracts complement, rather than replace, device checks of the same cards. */
class DesignCardResourcesTest {

    private val resources = listOf(File("src/main/res"), File("app/src/main/res"))
        .first { it.isDirectory }
    // Keyed on the resource set, not on which file declares it: a values/ reshuffle is not a regression.
    private val values = File(resources, "values").listFiles { file -> file.extension == "xml" }
        .orEmpty()
        .sortedBy { it.name }
        .map { "values/${it.name}" }
    private val dimensions = values.flatMap { elements(it, "dimen") }
        .associate { it.getAttribute("name") to it.textContent.trim() }
    private val styles = values.flatMap { elements(it, "style") }
        .associateBy { it.getAttribute("name") }

    @Test
    fun `card family keeps quiet surfaces and role specific shapes`() {
        val variants = mapOf(
            "Content" to "20dp",
            "Content.Outlined" to "20dp",
            "CompactSummary" to "20dp",
            "Summary" to "24dp",
            "Hero" to "28dp",
            "ScheduleDay" to "16dp",
            "SettingsGroup" to "20dp"
        )

        variants.forEach { (variant, radius) ->
            val style = cardStyle(variant)
            assertEquals(variant, radius, property(style, "cardCornerRadius"))
            assertEquals(variant, "0dp", property(style, "cardElevation"))
            assertEquals(
                variant,
                if (variant == "CompactSummary" || variant == "Hero") "?attr/colorSurfaceContainer" else "?attr/colorSurfaceContainerLow",
                property(style, "cardBackgroundColor")
            )
            assertEquals(
                variant,
                if (variant == "Content.Outlined") "1dp" else "0dp",
                property(style, "strokeWidth")
            )
        }
        assertEquals("?attr/colorOutlineVariant", property(cardStyle("Content.Outlined"), "strokeColor"))
    }

    @Test
    fun `shared card dimensions keep their values`() {
        // The compact sport rows (4 dp half gap) are Compose since L11 LP-6 (`SportCardSurface` of
        // :shared:feature-sport) and the schedule days since L10 LS-6b (`ScheduleDayCard` of :shared:feature-schedule).
        assertEquals("16dp", dimensions.getValue("design_card_padding"))
        assertEquals("20dp", dimensions.getValue("design_summary_padding"))
        assertEquals("48dp", dimensions.getValue("design_touch_target"))
    }

    @Test
    fun `connected groups join rows with small gaps and inner corners`() {
        assertEquals("20dp", dimensions.getValue("design_group_radius_outer"))
        assertEquals("4dp", dimensions.getValue("design_group_radius_inner"))
        assertEquals("2dp", dimensions.getValue("design_group_gap"))
        // Rows of a group are drawn by core/ui/ConnectedGroup.kt, never by a card of their own.
        listOf("item_subject_link")
            .forEach { layout -> assertEquals(layout, emptyList<Element>(), elements("layout/$layout.xml", MATERIAL_CARD)) }
    }

    @Test
    fun `settings stay unoutlined`() {
        val settingsCard = "Widget.ItmoWidgets.CompactSettingsCard"
        assertEquals("?attr/colorSurfaceContainerLow", property(settingsCard, "cardBackgroundColor"))
        assertEquals("0dp", property(settingsCard, "strokeWidth"))
        assertEquals("0dp", property(settingsCard, "cardElevation"))
        assertEquals("false", property(settingsCard, "cardUseCompatPadding"))
        assertEquals("false", property(settingsCard, "cardPreventCornerOverlap"))
    }

    private fun document(path: String): Element = DocumentBuilderFactory.newInstance()
        .apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        .newDocumentBuilder()
        .parse(File(resources, path))
        .documentElement

    private fun elements(path: String, tag: String): List<Element> {
        val root = document(path)
        val descendants = root.getElementsByTagName(tag)
        return buildList {
            if (root.tagName == tag) add(root)
            for (index in 0 until descendants.length) add(descendants.item(index) as Element)
        }
    }

    private fun property(styleName: String, attribute: String): String {
        val style = styles.getValue(styleName)
        val items = style.getElementsByTagName("item")
        for (index in 0 until items.length) {
            val item = items.item(index) as Element
            if (item.getAttribute("name") == attribute) return resolve(item.textContent.trim())
        }
        return property(style.getAttribute("parent"), attribute)
    }

    private fun resolve(value: String): String = if (value.startsWith("@dimen/")) {
        dimensions.getValue(value.removePrefix("@dimen/"))
    } else {
        value
    }

    private fun cardStyle(variant: String): String = "Widget.ItmoWidgets.Card.$variant"

    private companion object {
        const val MATERIAL_CARD = "com.google.android.material.card.MaterialCardView"
    }
}
