package dev.alllexey.itmowidgets.designsystem

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import dev.alllexey.itmowidgets.designsystem.theme.ExtendedColorTokens
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoSpacing
import dev.alllexey.itmowidgets.designsystem.tokens.ShapeTokens
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The Compose kit's [ExtendedColorTokens], [ShapeTokens] and [ItmoSpacing] equal the View screens'
 * `res/values{,-night}/colors.xml` and the `design_*` dimens of `res/values/dimens.xml`, so a value changed on one
 * side only fails here. The widget palette, `shortcut_icon_background` and `calendar_app` are resource-only;
 * feature dimens (no `design_` prefix) stay with their layouts. [m3eDivergences] are the dimens the M3E token change
 * (M3-02) moved in the kit only: the View screens keep their 2.2 values until their port deletes them.
 */
class DesignTokensParityTest {

    private val resources = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    @Test
    fun `light tokens equal values colors`() = assertParity(ExtendedColorTokens.Light, colors("values"))

    @Test
    fun `dark tokens equal values-night colors over values`() =
        assertParity(ExtendedColorTokens.Dark, colors("values") + colors("values-night"))

    @Test
    fun `shape and spacing tokens equal the design dimens`() {
        val dimens = elements("values", "dimens", "dimen")
            .filterKeys { it.startsWith("design_") && it !in m3eDivergences }
        assertEquals("design_* dimens without a token slot", dimens.keys.toSortedSet(), dimenSlots.keys.toSortedSet())
        dimenSlots.forEach { (name, token) -> assertEquals(name, dimens.getValue(name), token.resource()) }
    }

    private val dimenSlots: Map<String, Dp> = with(ItmoSpacing.Default) {
        mapOf(
            "design_spacing_related" to related,
            "design_spacing_compact" to compact,
            "design_spacing_content" to content,
            "design_spacing_group" to group,
            "design_spacing_section" to section,
            "design_screen_margin" to screenMargin,
            "design_card_padding" to cardPadding,
            "design_summary_padding" to summaryPadding,
            "design_touch_target" to touchTarget,
            "design_fab_stack_clearance" to fabStackClearance,
            "design_state_padding" to statePadding,
            "design_state_icon" to stateIcon,
            "design_state_inline_icon" to stateInlineIcon,
            "design_card_radius_day" to ShapeTokens.ScheduleDay,
            "design_card_radius_content" to ShapeTokens.CardContent,
            "design_card_radius_hero" to ShapeTokens.CardHero,
            "design_card_elevation" to ShapeTokens.CardElevation,
            "design_card_stroke" to ShapeTokens.CardStroke,
            "design_group_radius_outer" to ShapeTokens.GroupOuter,
            "design_group_radius_inner" to ShapeTokens.GroupInner,
            "design_group_gap" to ShapeTokens.GroupGap,
        )
    }

    /** `Card.Summary` stays 24 dp in `res`; the kit's `cardSummary` is 28 dp (owner, item 14 Q2 (a)). */
    private val m3eDivergences = setOf("design_card_radius_summary")

    @Test
    fun `the M3E divergences still exist in res and differ from the kit`() {
        val dimens = elements("values", "dimens", "dimen")
        assertEquals("24dp", dimens["design_card_radius_summary"])
        assertEquals("28dp", ShapeTokens.CardSummary.resource())
    }

    @Test
    fun `the window background below API 31 equals the static scheme's background`() {
        listOf("values" to "light", "values-night" to "dark").forEach { (directory, mode) ->
            val window = elements(directory, "theme_colors", "color").getValue("theme_static_background")
            assertEquals("$directory theme_static_background", staticBackground(mode), argb(window))
        }
    }

    /** `color.scheme.<mode>.background` of the token export, as eight upper-case hex digits. */
    private fun staticBackground(mode: String): String {
        val json = listOf(File(TOKENS), File("../$TOKENS")).first { it.isFile }.readText()
        val scheme = json.substring(json.indexOf("\"scheme\""))
        val roles = scheme.substring(scheme.indexOf("\"$mode\""))
        val value = Regex("\"background\": \"(#[0-9A-F]{6})\"").find(roles)?.groupValues?.get(1)
        return argb(checkNotNull(value) { "no $mode background in $TOKENS" })
    }

    private fun assertParity(tokens: ExtendedColorTokens, colors: Map<String, String>) {
        val shared = colors.keys.filterNot(::isResourceOnly).toSortedSet()
        assertEquals("colors.xml entries without a token slot", shared, slots(tokens).keys.toSortedSet())
        slots(tokens).forEach { (name, token) ->
            assertEquals(name, argb(colors.getValue(name)), token.toArgb().hex())
        }
    }

    private fun slots(tokens: ExtendedColorTokens) = with(tokens) {
        mapOf(
            "lesson_type_lecture" to lessonTypeLecture,
            "lesson_type_lab" to lessonTypeLab,
            "lesson_type_practice" to lessonTypePractice,
            "lesson_type_assessment" to lessonTypeAssessment,
            "lesson_type_consultation" to lessonTypeConsultation,
            "lesson_type_sport" to lessonTypeSport,
            "lesson_type_free" to lessonTypeFree,
            "lesson_type_default" to lessonTypeDefault,
            "recordbook_passed" to recordbookPassed,
            "sport_score_attendance" to sportScoreAttendance,
            "sport_score_bonus" to sportScoreBonus,
            "sport_condition_allowed" to sportConditionAllowed,
            "sport_condition_waiting" to sportConditionWaiting,
            "sport_condition_warning" to sportConditionWarning,
            "sport_condition_blocked" to sportConditionBlocked,
            "teacher_level_very_negative" to teacherLevelVeryNegative,
            "teacher_level_negative" to teacherLevelNegative,
            "teacher_level_mixed" to teacherLevelMixed,
            "teacher_level_positive" to teacherLevelPositive,
            "teacher_level_very_positive" to teacherLevelVeryPositive,
        )
    }

    private fun isResourceOnly(name: String) =
        name.startsWith("widget_") || name == "shortcut_icon_background" || name == "calendar_app"

    /** `name -> value` of every `<color>` in `<directory>/colors.xml`; a missing file has none. */
    private fun colors(directory: String): Map<String, String> = elements(directory, "colors", "color")

    /** `name -> text` of every `<tag>` in `<directory>/<file>.xml`; a missing file has none. */
    private fun elements(directory: String, file: String, tag: String): Map<String, String> {
        val xml = File(resources, "$directory/$file.xml")
        if (!xml.isFile) return emptyMap()
        val root = DocumentBuilderFactory.newInstance()
            .apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            .newDocumentBuilder()
            .parse(xml)
            .documentElement
        val nodes = root.getElementsByTagName(tag)
        return (0 until nodes.length).associate { index ->
            val element = nodes.item(index) as Element
            element.getAttribute("name") to element.textContent.trim()
        }
    }

    /** `#RRGGBB` or `#AARRGGBB` as eight upper-case hex digits. */
    private fun argb(value: String): String {
        val digits = value.removePrefix("#").uppercase()
        return when (digits.length) {
            6 -> "FF$digits"
            8 -> digits
            else -> error("Unexpected colour literal $value")
        }
    }

    private fun Int.hex() = "%08X".format(this)

    private companion object {
        const val TOKENS = "shared/designsystem/tokens/itmo-tokens.json"
    }

    /** `16dp`, or `0.5dp` for a fractional token, as a dimen resource writes it. */
    private fun Dp.resource(): String = if (value % 1f == 0f) "${value.toInt()}dp" else "${value}dp"
}
