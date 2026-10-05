package dev.alllexey.itmowidgets.designsystem

import androidx.compose.ui.graphics.toArgb
import dev.alllexey.itmowidgets.designsystem.theme.ExtendedColorTokens
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The Compose kit's [ExtendedColorTokens] equal the View screens' `res/values{,-night}/colors.xml`, so a colour
 * changed on one side only fails here. The widget palette, `shortcut_icon_background` and `calendar_app` are
 * resource-only.
 */
class DesignTokensParityTest {

    private val resources = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    @Test
    fun `light tokens equal values colors`() = assertParity(ExtendedColorTokens.Light, colors("values"))

    @Test
    fun `dark tokens equal values-night colors over values`() =
        assertParity(ExtendedColorTokens.Dark, colors("values") + colors("values-night"))

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
    private fun colors(directory: String): Map<String, String> {
        val file = File(resources, "$directory/colors.xml")
        if (!file.isFile) return emptyMap()
        val root = DocumentBuilderFactory.newInstance()
            .apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            .newDocumentBuilder()
            .parse(file)
            .documentElement
        val nodes = root.getElementsByTagName("color")
        return (0 until nodes.length).associate { index ->
            val color = nodes.item(index) as Element
            color.getAttribute("name") to color.textContent.trim()
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
}
