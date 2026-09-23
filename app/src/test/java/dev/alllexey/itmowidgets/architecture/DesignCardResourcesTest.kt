package dev.alllexey.itmowidgets.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            "ScheduleDay" to "16dp",
            "SettingsGroup" to "20dp"
        )

        variants.forEach { (variant, radius) ->
            val style = cardStyle(variant)
            assertEquals(variant, radius, property(style, "cardCornerRadius"))
            assertEquals(variant, "0dp", property(style, "cardElevation"))
            assertEquals(
                variant,
                if (variant == "CompactSummary") "?attr/colorSurfaceContainer" else "?attr/colorSurfaceContainerLow",
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
    fun `approved cards consume shared variants without local appearance overrides`() {
        val layouts = mapOf(
            "item_sport_lesson" to "Content.Outlined",
            "item_sport_booking" to "Content.Outlined",
            "item_recordbook_subject" to "Content",
            "item_recordbook_control" to "Content",
            "item_recordbook_note" to "Content",
            "item_recordbook_summary" to "CompactSummary",
            "item_subject_hero" to "Summary",
            "item_recordbook_sport" to "Summary",
            "item_day_schedule" to "ScheduleDay"
        )

        layouts.forEach { (layout, variant) ->
            val card = elements("layout/$layout.xml", MATERIAL_CARD).first()
            assertEquals(layout, "@style/${cardStyle(variant)}", card.getAttribute("style"))
            appearanceAttributes.forEach { attribute ->
                assertFalse("$layout overrides $attribute", card.hasAttribute("app:$attribute"))
            }
        }
    }

    @Test
    fun `schedule days retain wider spacing than compact sport rows`() {
        mapOf(
            "item_day_schedule" to "8dp",
            "item_sport_booking" to "4dp",
            "item_sport_lesson" to "4dp"
        ).forEach { (layout, halfGap) ->
            val row = document("layout/$layout.xml")
            assertEquals(layout, "16dp", resolve(row.getAttribute("android:paddingHorizontal")))
            assertEquals(layout, halfGap, resolve(row.getAttribute("android:paddingVertical")))
        }
        assertEquals("16dp", dimensions.getValue("design_card_padding"))
        assertEquals("20dp", dimensions.getValue("design_summary_padding"))
        assertEquals("48dp", dimensions.getValue("design_touch_target"))
    }

    @Test
    fun `settings stay unoutlined and debug cards declare their quiet surface`() {
        listOf("SettingsCard", "CompactSettingsCard").forEach { variant ->
            val style = "Widget.ItmoWidgets.$variant"
            assertEquals(variant, "?attr/colorSurfaceContainerLow", property(style, "cardBackgroundColor"))
            assertEquals(variant, "0dp", property(style, "strokeWidth"))
            assertEquals(variant, "0dp", property(style, "cardElevation"))
            assertEquals(variant, "false", property(style, "cardUseCompatPadding"))
            assertEquals(variant, "false", property(style, "cardPreventCornerOverlap"))
        }
        val debugCards = elements("layout/fragment_debug_tools.xml", MATERIAL_CARD)
        assertFalse(debugCards.isEmpty())
        debugCards.forEach { card ->
            assertEquals("@style/${cardStyle("Content.Outlined")}", card.getAttribute("style"))
        }
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
        val appearanceAttributes = listOf(
            "cardBackgroundColor", "cardCornerRadius", "cardElevation", "strokeWidth", "strokeColor"
        )
    }
}
