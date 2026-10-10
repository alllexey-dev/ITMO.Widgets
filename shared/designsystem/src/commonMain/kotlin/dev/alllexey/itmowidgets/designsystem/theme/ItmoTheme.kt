package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.platform.LocalItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.platform.defaultPlatformStyle
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoEmphasizedTypography
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoIosTypography
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoMaterialTypography
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoMotion
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoShapes
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoSpacing
import dev.alllexey.itmowidgets.designsystem.tokens.LocalItmoEmphasizedTypography
import dev.alllexey.itmowidgets.designsystem.tokens.LocalItmoMotion
import dev.alllexey.itmowidgets.designsystem.tokens.LocalItmoShapes
import dev.alllexey.itmowidgets.designsystem.tokens.LocalItmoSpacing
import dev.alllexey.itmowidgets.designsystem.tokens.emphasizedTypographyOf
import dev.alllexey.itmowidgets.designsystem.tokens.toMaterialShapes

/**
 * The app's Material theme for Compose: the scheme from [colorSource] in [dark] or light mode, the app's own colours,
 * shapes, spacing, type and motion, at the owner's M3E values (item 14, M3-02): the brand static scheme below
 * Android 12 and on iOS, `cardSummary` on the M3E scale, the expressive switch on. Without a [colorSource] it follows
 * the user's accent colour setting ([AppColorSource]).
 *
 * [expressive] is the one switch of the kit's Material 3 Expressive variants ([ItmoTheme.expressive]): the loading
 * indicator, the pull-to-refresh indicator, the connected button group, wavy progress, the hero avatar mask and
 * [ItmoTheme.heroMotionScheme]. It is on for every screen; previews and tests turn it off to show the standard
 * component next to the expressive one.
 *
 * [platformStyle] is the look of the kit ([ItmoPlatformStyle]): Material on Android, iOS on iOS. The iOS style brings
 * Apple's type scale, UIKit's spacing, radii and touch target and the iOS colour slot, and ignores [expressive]: the
 * M3E look is Material's only.
 */
@Composable
fun ItmoTheme(
    dark: Boolean = isSystemInDarkTheme(),
    colorSource: ColorSource = AppColorSource.current,
    expressive: Boolean = true,
    platformStyle: ItmoPlatformStyle = defaultPlatformStyle(),
    content: @Composable () -> Unit,
) {
    val scheme = colorSchemeOf(colorSource, dark)
    val extended = remember(scheme, dark) { ExtendedColorTokens.of(dark).resolve(scheme) }
    val tokens = StyleTokens.of(platformStyle)
    CompositionLocalProvider(
        LocalItmoPlatformStyle provides platformStyle,
        LocalItmoExtendedColors provides extended,
        LocalItmoIosColors provides ItmoIosColors.of(dark),
        LocalItmoShapes provides tokens.shapes,
        LocalItmoSpacing provides tokens.spacing,
        LocalItmoEmphasizedTypography provides tokens.emphasized,
        LocalItmoMotion provides ItmoMotion.Default,
        LocalMinimumInteractiveComponentSize provides platformStyle.minTouchTarget,
    ) {
        ItmoMaterialTheme(scheme, expressive && platformStyle == ItmoPlatformStyle.Material, content)
    }
}

/**
 * Sets the expressive switch for [content] inside an [ItmoTheme] and keeps its scheme and tokens; for previews and
 * tests that show both states side by side. Screens never call it: the switch is the theme's.
 */
@Composable
internal fun ProvideItmoExpressive(expressive: Boolean, content: @Composable () -> Unit) {
    val material = LocalItmoPlatformStyle.current == ItmoPlatformStyle.Material
    ItmoMaterialTheme(MaterialTheme.colorScheme, expressive && material, content)
}

/**
 * Material's theme over the kit's tokens. Expressive or not, components animate with the standard motion scheme;
 * only a hero takes [ItmoTheme.heroMotionScheme].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ItmoMaterialTheme(scheme: ColorScheme, expressive: Boolean, content: @Composable () -> Unit) {
    val tokens = StyleTokens.of(LocalItmoPlatformStyle.current)
    CompositionLocalProvider(LocalItmoExpressive provides expressive) {
        if (expressive) {
            MaterialExpressiveTheme(
                colorScheme = scheme,
                motionScheme = ItmoMotion.Default.scheme,
                shapes = tokens.materialShapes,
                typography = tokens.typography,
                content = content,
            )
        } else {
            MaterialTheme(
                colorScheme = scheme,
                motionScheme = ItmoMotion.Default.scheme,
                shapes = tokens.materialShapes,
                typography = tokens.typography,
                content = content,
            )
        }
    }
}

/** The current theme's tokens; components read them only through here. */
object ItmoTheme {
    val colorScheme: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    /** The app's own colours under the current scheme; outside an [ItmoTheme] the static light ones. */
    val extendedColors: ItmoExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoExtendedColors.current

    /** The M3 type roles. */
    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    /** The emphasized twin of every M3 type role. */
    val emphasizedTypography: ItmoEmphasizedTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoEmphasizedTypography.current

    /** The corner scale and the card family. */
    val shapes: ItmoShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoShapes.current

    val spacing: ItmoSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoSpacing.current

    val motion: ItmoMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoMotion.current

    /** The look the kit draws in; read only inside the kit, never in feature code (Konsist, KN-02b). */
    internal val platformStyle: ItmoPlatformStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoPlatformStyle.current

    /** UIKit's system colours for the iOS variants, light or dark like the scheme. */
    internal val iosColors: ItmoIosColors
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoIosColors.current

    /** True when the kit's expressive variants are on: the default of [ItmoTheme], Material style only. */
    val expressive: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoExpressive.current

    /**
     * What a hero moment animates with (the QR pass reveal, the profile hero, the sport score ring, the lesson in
     * progress): `MotionScheme.expressive()` when [expressive], the components' standard scheme otherwise.
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val heroMotionScheme: MotionScheme
        @Composable
        @ReadOnlyComposable
        get() = if (LocalItmoExpressive.current) ExpressiveMotionScheme else MaterialTheme.motionScheme
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ExpressiveMotionScheme = MotionScheme.expressive()

internal val LocalItmoExpressive = staticCompositionLocalOf { false }

/** The tokens of one platform style, built once. */
private class StyleTokens(val shapes: ItmoShapes, val spacing: ItmoSpacing, val typography: Typography) {
    val materialShapes = shapes.toMaterialShapes()
    val emphasized = emphasizedTypographyOf(typography)

    companion object {
        private val Material = StyleTokens(ItmoShapes.Default, ItmoSpacing.Default, ItmoMaterialTypography)
        private val Ios = StyleTokens(ItmoShapes.Ios, ItmoSpacing.Ios, ItmoIosTypography)

        fun of(style: ItmoPlatformStyle) = when (style) {
            ItmoPlatformStyle.Material -> Material
            ItmoPlatformStyle.Ios -> Ios
        }
    }
}

internal fun generatedColorScheme(colorSource: ColorSource, dark: Boolean): ColorScheme = when (colorSource) {
    is ColorSource.Seed -> seededColorScheme(colorSource.argb, dark)
    is ColorSource.Accent -> accentColorScheme(colorSource.argb, dark)
    ColorSource.Platform, ColorSource.Static -> staticColorScheme(dark)
}

/** The platform's dynamic scheme, or null where there is none (Android below 12, iOS). */
@Composable
internal expect fun platformColorScheme(dark: Boolean): ColorScheme?
