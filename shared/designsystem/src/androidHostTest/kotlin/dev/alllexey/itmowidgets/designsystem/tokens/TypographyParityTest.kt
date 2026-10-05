package dev.alllexey.itmowidgets.designsystem.tokens

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.google.android.material.R as MaterialR

/**
 * Every type role and its emphasized twin equal MDC 1.13's `TextAppearance.Material3.*` (and `.Emphasized`): size,
 * line height, tracking (`letterSpacing` em times size) and weight (`sans-serif-medium` 500, bold 700).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TypographyParityTest {

    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), MaterialR.style.Theme_Material3_DayNight)

    @Test
    fun `type roles equal the MDC text appearances`() {
        val mismatches = TypeScaleTokens.roles.flatMap { (name, roles) ->
            val style = name.replaceFirstChar(Char::uppercase)
            listOfNotNull(
                compare(name, roles.first, appearance("TextAppearance.Material3.$style")),
                compare("${name}Emphasized", roles.second, appearance("TextAppearance.Material3.$style.Emphasized")),
            )
        }
        assertEquals(emptyList<String>(), mismatches)
    }

    @Test
    fun `material typography carries the tokens on the system font`() {
        val typography = ItmoMaterialTypography
        val emphasized = emphasizedTypographyOf(typography)
        TypeScaleTokens.roles.forEach { (name, roles) ->
            assertStyle(name, roles.first, typography.role(name))
            assertStyle("${name}Emphasized", roles.second, emphasized.role(name))
        }
    }

    private fun compare(name: String, token: TypeRole, view: TypeRole): String? =
        if (token == view) null else "$name: tokens $token / view $view"

    /** A View text appearance read as a [TypeRole]; tracking rounded to 0.01 sp against float em values. */
    private fun appearance(styleName: String): TypeRole {
        val style = MaterialR.style::class.java.getField(styleName.replace('.', '_')).getInt(null)
        fun <T> read(attr: Int, value: android.content.res.TypedArray.() -> T): T {
            val array = context.obtainStyledAttributes(style, intArrayOf(attr))
            return try { array.value() } finally { array.recycle() }
        }
        val size = read(android.R.attr.textSize) { sp(peekValue(0)) }
        val lineHeight = read(MaterialR.attr.lineHeight) { sp(peekValue(0)) }
        val letterSpacing = read(android.R.attr.letterSpacing) { getFloat(0, 0f) }
        val family = read(android.R.attr.fontFamily) { getString(0) }
        val textStyle = read(android.R.attr.textStyle) { getInt(0, Typeface.NORMAL) }
        val weight = when {
            textStyle and Typeface.BOLD != 0 -> 700
            family == "sans-serif-medium" -> 500
            else -> 400
        }
        val tracking = Math.round(letterSpacing * size * 100) / 100f
        return TypeRole(size, lineHeight, tracking, weight)
    }

    private fun sp(value: TypedValue): Float {
        check(value.type == TypedValue.TYPE_DIMENSION) { "not a dimension: $value" }
        check(value.data and TypedValue.COMPLEX_UNIT_MASK == TypedValue.COMPLEX_UNIT_SP) { "not sp: $value" }
        return TypedValue.complexToFloat(value.data)
    }

    private fun assertStyle(name: String, role: TypeRole, style: TextStyle) {
        assertEquals("$name family", FontFamily.Default, style.fontFamily)
        assertEquals("$name weight", role.weight, style.fontWeight?.weight)
        assertEquals("$name size", role.size, style.fontSize.value)
        assertEquals("$name line height", role.lineHeight, style.lineHeight.value)
        assertEquals("$name tracking", role.tracking, style.letterSpacing.value)
    }

    private fun Typography.role(name: String): TextStyle = when (name) {
        "displayLarge" -> displayLarge
        "displayMedium" -> displayMedium
        "displaySmall" -> displaySmall
        "headlineLarge" -> headlineLarge
        "headlineMedium" -> headlineMedium
        "headlineSmall" -> headlineSmall
        "titleLarge" -> titleLarge
        "titleMedium" -> titleMedium
        "titleSmall" -> titleSmall
        "bodyLarge" -> bodyLarge
        "bodyMedium" -> bodyMedium
        "bodySmall" -> bodySmall
        "labelLarge" -> labelLarge
        "labelMedium" -> labelMedium
        "labelSmall" -> labelSmall
        else -> error(name)
    }

    private fun ItmoEmphasizedTypography.role(name: String): TextStyle = when (name) {
        "displayLarge" -> displayLarge
        "displayMedium" -> displayMedium
        "displaySmall" -> displaySmall
        "headlineLarge" -> headlineLarge
        "headlineMedium" -> headlineMedium
        "headlineSmall" -> headlineSmall
        "titleLarge" -> titleLarge
        "titleMedium" -> titleMedium
        "titleSmall" -> titleSmall
        "bodyLarge" -> bodyLarge
        "bodyMedium" -> bodyMedium
        "bodySmall" -> bodySmall
        "labelLarge" -> labelLarge
        "labelMedium" -> labelMedium
        "labelSmall" -> labelSmall
        else -> error(name)
    }
}
