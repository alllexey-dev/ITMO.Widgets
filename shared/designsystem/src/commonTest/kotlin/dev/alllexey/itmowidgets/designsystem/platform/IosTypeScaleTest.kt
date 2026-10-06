package dev.alllexey.itmowidgets.designsystem.platform

import dev.alllexey.itmowidgets.designsystem.tokens.IosTextStyle
import dev.alllexey.itmowidgets.designsystem.tokens.IosTypeScaleTokens
import dev.alllexey.itmowidgets.designsystem.tokens.TypeScaleTokens
import kotlin.test.Test
import kotlin.test.assertEquals

class IosTypeScaleTest {
    @Test
    fun everyM3RoleTakesAnAppleTextStyle() {
        assertEquals(TypeScaleTokens.roles.keys.toList(), IosTypeScaleTokens.styles.keys.toList())
        val expected = mapOf(
            "displayLarge" to IosTextStyle.LargeTitle,
            "displayMedium" to IosTextStyle.LargeTitle,
            "displaySmall" to IosTextStyle.LargeTitle,
            "headlineLarge" to IosTextStyle.Title1,
            "headlineMedium" to IosTextStyle.Title1,
            "headlineSmall" to IosTextStyle.Title2,
            "titleLarge" to IosTextStyle.Title3,
            "titleMedium" to IosTextStyle.Headline,
            "titleSmall" to IosTextStyle.Subheadline,
            "bodyLarge" to IosTextStyle.Body,
            "bodyMedium" to IosTextStyle.Subheadline,
            "bodySmall" to IosTextStyle.Footnote,
            "labelLarge" to IosTextStyle.Subheadline,
            "labelMedium" to IosTextStyle.Footnote,
            "labelSmall" to IosTextStyle.Caption1,
        )
        assertEquals(expected, IosTypeScaleTokens.styles.mapValues { it.value.first })
    }

    @Test
    fun rolesCarryTheStyleMetricsAndEmphasizedTwinsAreSemibold() {
        IosTypeScaleTokens.roles.forEach { (name, roles) ->
            val (style, weight) = IosTypeScaleTokens.styles.getValue(name)
            val (role, twin) = roles
            assertEquals(style.size, role.size, name)
            assertEquals(style.lineHeight, role.lineHeight, name)
            assertEquals(weight, role.weight, name)
            assertEquals(SEMIBOLD, twin.weight, name)
            assertEquals(style.size, twin.size, name)
        }
    }

    @Test
    fun appleSizesAtTheLargeContentSize() {
        val sizes = IosTextStyle.entries.associate { it.name to (it.size to it.lineHeight) }
        assertEquals(34f to 41f, sizes["LargeTitle"])
        assertEquals(17f to 22f, sizes["Body"])
        assertEquals(11f to 13f, sizes["Caption2"])
        assertEquals(SEMIBOLD, IosTextStyle.Headline.weight)
    }

    private companion object {
        const val SEMIBOLD = 600
    }
}
