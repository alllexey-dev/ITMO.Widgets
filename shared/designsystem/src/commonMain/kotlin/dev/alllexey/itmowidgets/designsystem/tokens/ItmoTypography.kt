package dev.alllexey.itmowidgets.designsystem.tokens

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** One type role: size, line height and tracking in sp, weight on the 100-900 scale. */
@Immutable
data class TypeRole(val size: Float, val lineHeight: Float, val tracking: Float, val weight: Int)

/**
 * The M3 type scale on the system font (Roboto, SF; 03 Q11) at MDC 1.13's `TextAppearance.Material3.*` values, each
 * role with its `.Emphasized` twin. Pinned here rather than taken from material3's defaults, so a material3 bump
 * cannot change the text of a ported screen unnoticed (M3E keeps the type scale, item 14); `TypographyParityTest`
 * compares them.
 */
object TypeScaleTokens {
    private const val REGULAR = 400
    private const val MEDIUM = 500
    private const val BOLD = 700

    /** `name -> (role, emphasized role)` in Material's order, `displayLarge` to `labelSmall`. */
    val roles: Map<String, Pair<TypeRole, TypeRole>> = linkedMapOf(
        "displayLarge" to (TypeRole(57f, 64f, -0.25f, REGULAR) to TypeRole(57f, 64f, 0f, MEDIUM)),
        "displayMedium" to (TypeRole(45f, 52f, 0f, REGULAR) to TypeRole(45f, 52f, 0f, MEDIUM)),
        "displaySmall" to (TypeRole(36f, 44f, 0f, REGULAR) to TypeRole(36f, 44f, 0f, MEDIUM)),
        "headlineLarge" to (TypeRole(32f, 40f, 0f, REGULAR) to TypeRole(32f, 40f, 0f, MEDIUM)),
        "headlineMedium" to (TypeRole(28f, 36f, 0f, REGULAR) to TypeRole(28f, 36f, 0f, MEDIUM)),
        "headlineSmall" to (TypeRole(24f, 32f, 0f, REGULAR) to TypeRole(24f, 32f, 0f, MEDIUM)),
        "titleLarge" to (TypeRole(22f, 28f, 0f, REGULAR) to TypeRole(22f, 28f, 0f, MEDIUM)),
        "titleMedium" to (TypeRole(16f, 24f, 0.15f, MEDIUM) to TypeRole(16f, 24f, 0.15f, BOLD)),
        "titleSmall" to (TypeRole(14f, 20f, 0.1f, MEDIUM) to TypeRole(14f, 20f, 0.1f, BOLD)),
        "bodyLarge" to (TypeRole(16f, 24f, 0.5f, REGULAR) to TypeRole(16f, 24f, 0.15f, MEDIUM)),
        "bodyMedium" to (TypeRole(14f, 20f, 0.25f, REGULAR) to TypeRole(14f, 20f, 0.25f, MEDIUM)),
        "bodySmall" to (TypeRole(12f, 16f, 0.4f, REGULAR) to TypeRole(12f, 16f, 0.4f, MEDIUM)),
        "labelLarge" to (TypeRole(14f, 20f, 0.1f, MEDIUM) to TypeRole(14f, 20f, 0.1f, BOLD)),
        "labelMedium" to (TypeRole(12f, 16f, 0.5f, MEDIUM) to TypeRole(12f, 16f, 0.5f, BOLD)),
        "labelSmall" to (TypeRole(11f, 16f, 0.5f, MEDIUM) to TypeRole(11f, 16f, 0.5f, BOLD)),
    )
}

/** The emphasized slot: the `*Emphasized` twin of every M3 role, for the few places that stress one value. */
@Immutable
data class ItmoEmphasizedTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
)

/**
 * Material's [Typography] with every role and emphasized role from [TypeScaleTokens]. Each style starts from
 * material3's own role, so its platform text settings and line-height alignment stay Material's.
 */
internal val ItmoMaterialTypography: Typography = typographyOf(TypeScaleTokens.roles)

/** Material's [Typography] with every role and emphasized role from [roles] (`name -> (role, emphasized role)`). */
internal fun typographyOf(roles: Map<String, Pair<TypeRole, TypeRole>>): Typography {
    val defaults = Typography()
    fun role(name: String, template: TextStyle) = template.with(roles.getValue(name).first)
    fun emphasized(name: String, template: TextStyle) = template.with(roles.getValue(name).second)
    return Typography(
        displayLarge = role("displayLarge", defaults.displayLarge),
        displayMedium = role("displayMedium", defaults.displayMedium),
        displaySmall = role("displaySmall", defaults.displaySmall),
        headlineLarge = role("headlineLarge", defaults.headlineLarge),
        headlineMedium = role("headlineMedium", defaults.headlineMedium),
        headlineSmall = role("headlineSmall", defaults.headlineSmall),
        titleLarge = role("titleLarge", defaults.titleLarge),
        titleMedium = role("titleMedium", defaults.titleMedium),
        titleSmall = role("titleSmall", defaults.titleSmall),
        bodyLarge = role("bodyLarge", defaults.bodyLarge),
        bodyMedium = role("bodyMedium", defaults.bodyMedium),
        bodySmall = role("bodySmall", defaults.bodySmall),
        labelLarge = role("labelLarge", defaults.labelLarge),
        labelMedium = role("labelMedium", defaults.labelMedium),
        labelSmall = role("labelSmall", defaults.labelSmall),
        displayLargeEmphasized = emphasized("displayLarge", defaults.displayLarge),
        displayMediumEmphasized = emphasized("displayMedium", defaults.displayMedium),
        displaySmallEmphasized = emphasized("displaySmall", defaults.displaySmall),
        headlineLargeEmphasized = emphasized("headlineLarge", defaults.headlineLarge),
        headlineMediumEmphasized = emphasized("headlineMedium", defaults.headlineMedium),
        headlineSmallEmphasized = emphasized("headlineSmall", defaults.headlineSmall),
        titleLargeEmphasized = emphasized("titleLarge", defaults.titleLarge),
        titleMediumEmphasized = emphasized("titleMedium", defaults.titleMedium),
        titleSmallEmphasized = emphasized("titleSmall", defaults.titleSmall),
        bodyLargeEmphasized = emphasized("bodyLarge", defaults.bodyLarge),
        bodyMediumEmphasized = emphasized("bodyMedium", defaults.bodyMedium),
        bodySmallEmphasized = emphasized("bodySmall", defaults.bodySmall),
        labelLargeEmphasized = emphasized("labelLarge", defaults.labelLarge),
        labelMediumEmphasized = emphasized("labelMedium", defaults.labelMedium),
        labelSmallEmphasized = emphasized("labelSmall", defaults.labelSmall),
    )
}

/** The emphasized roles of [typography] as the kit's own slot, independent of where material3 keeps them. */
internal fun emphasizedTypographyOf(typography: Typography) = ItmoEmphasizedTypography(
    displayLarge = typography.displayLargeEmphasized,
    displayMedium = typography.displayMediumEmphasized,
    displaySmall = typography.displaySmallEmphasized,
    headlineLarge = typography.headlineLargeEmphasized,
    headlineMedium = typography.headlineMediumEmphasized,
    headlineSmall = typography.headlineSmallEmphasized,
    titleLarge = typography.titleLargeEmphasized,
    titleMedium = typography.titleMediumEmphasized,
    titleSmall = typography.titleSmallEmphasized,
    bodyLarge = typography.bodyLargeEmphasized,
    bodyMedium = typography.bodyMediumEmphasized,
    bodySmall = typography.bodySmallEmphasized,
    labelLarge = typography.labelLargeEmphasized,
    labelMedium = typography.labelMediumEmphasized,
    labelSmall = typography.labelSmallEmphasized,
)

private fun TextStyle.with(role: TypeRole) = copy(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight(role.weight),
    fontSize = role.size.sp,
    lineHeight = role.lineHeight.sp,
    letterSpacing = role.tracking.sp,
)

internal val LocalItmoEmphasizedTypography = staticCompositionLocalOf { emphasizedTypographyOf(ItmoMaterialTypography) }
