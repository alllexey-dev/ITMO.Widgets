package dev.alllexey.itmowidgets.designsystem.tokens

import androidx.compose.material3.Typography

/**
 * Apple's text styles at the default Dynamic Type size (Large), on the system font: SF Pro on iOS through CMP's
 * default family. Size and line height in pt are Apple's (HIG, Typography). [tracking] and [semiboldTracking] are sp
 * per glyph, measured on the pinned simulator runtime (iOS 27.0, DS-IOS-01): UIKit's width of a Latin and a Cyrillic
 * line minus CMP 1.12.1's width of the same line, divided by its glyphs. CMP draws SF at one optical size (its width
 * per em does not change with the size), so up to 17 pt the value equals SF's tracking table and from 20 pt it also
 * takes in the narrower SF Display glyphs: lines measure and wrap as in UIKit.
 */
enum class IosTextStyle(
    val size: Float,
    val lineHeight: Float,
    val tracking: Float,
    val semiboldTracking: Float = tracking,
    val weight: Int = REGULAR,
) {
    LargeTitle(34f, 41f, -1.57f, -1.39f),
    Title1(28f, 34f, -1.23f, -1.09f),
    Title2(22f, 28f, -0.92f, -0.87f),
    Title3(20f, 25f, -0.72f, -0.70f),
    Headline(17f, 22f, -0.43f, weight = SEMIBOLD),
    Body(17f, 22f, -0.43f),
    Callout(16f, 21f, -0.31f),
    Subheadline(15f, 20f, -0.23f),
    Footnote(13f, 18f, -0.08f),
    Caption1(12f, 16f, 0f),
    Caption2(11f, 13f, 0.06f),
    ;

    /** This style in [weight], with the tracking that weight measured. */
    fun role(weight: Int = this.weight): TypeRole =
        TypeRole(size, lineHeight, if (weight >= SEMIBOLD) semiboldTracking else tracking, weight)
}

private const val REGULAR = 400
private const val MEDIUM = 500
private const val SEMIBOLD = 600

/**
 * The M3 roles on Apple's text styles, the type scale of `ItmoPlatformStyle.Ios`: every role takes the text style
 * that plays its part in UIKit; the emphasized twin is the same style in semibold.
 */
object IosTypeScaleTokens {
    /** `name -> (text style, weight)` in Material's order, `displayLarge` to `labelSmall`. */
    val styles: Map<String, Pair<IosTextStyle, Int>> = linkedMapOf(
        "displayLarge" to (IosTextStyle.LargeTitle to REGULAR),
        "displayMedium" to (IosTextStyle.LargeTitle to REGULAR),
        "displaySmall" to (IosTextStyle.LargeTitle to REGULAR),
        "headlineLarge" to (IosTextStyle.Title1 to REGULAR),
        "headlineMedium" to (IosTextStyle.Title1 to REGULAR),
        "headlineSmall" to (IosTextStyle.Title2 to REGULAR),
        "titleLarge" to (IosTextStyle.Title3 to REGULAR),
        "titleMedium" to (IosTextStyle.Headline to SEMIBOLD),
        "titleSmall" to (IosTextStyle.Subheadline to SEMIBOLD),
        "bodyLarge" to (IosTextStyle.Body to REGULAR),
        "bodyMedium" to (IosTextStyle.Subheadline to REGULAR),
        "bodySmall" to (IosTextStyle.Footnote to REGULAR),
        "labelLarge" to (IosTextStyle.Subheadline to MEDIUM),
        "labelMedium" to (IosTextStyle.Footnote to MEDIUM),
        "labelSmall" to (IosTextStyle.Caption1 to MEDIUM),
    )

    /** `name -> (role, emphasized role)`, the shape of [TypeScaleTokens.roles]. */
    val roles: Map<String, Pair<TypeRole, TypeRole>> = styles.mapValuesTo(linkedMapOf()) { (_, value) ->
        val (style, weight) = value
        style.role(weight) to style.role(SEMIBOLD)
    }
}

/** Material's [Typography] on [IosTypeScaleTokens]: `MaterialTheme.typography` under `ItmoPlatformStyle.Ios`. */
internal val ItmoIosTypography: Typography = typographyOf(IosTypeScaleTokens.roles)
