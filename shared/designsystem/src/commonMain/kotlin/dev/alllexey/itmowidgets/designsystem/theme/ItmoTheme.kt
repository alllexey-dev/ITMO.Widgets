package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoEmphasizedTypography
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
 * shapes, spacing, type and motion, at the app's M3E values: the brand static scheme below Android 12,
 * `cardSummary` on the M3E scale, the expressive switch on. Without a [colorSource] it follows the user's accent
 * colour setting ([AppColorSource]).
 *
 * [expressive] is the one switch of the kit's Material 3 Expressive variants ([ItmoTheme.expressive]): the loading
 * indicator, the pull-to-refresh indicator, the connected button group, wavy progress, the hero avatar mask and
 * [ItmoTheme.heroMotionScheme]. It is on for every screen; previews and tests turn it off to show the standard
 * component next to the expressive one.
 */
@Composable
fun ItmoTheme(
    dark: Boolean = isSystemInDarkTheme(),
    colorSource: ColorSource = AppColorSource.current,
    expressive: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = colorSchemeOf(colorSource, dark)
    val extended = remember(scheme, dark) { ExtendedColorTokens.of(dark).resolve(scheme) }
    CompositionLocalProvider(
        LocalItmoExtendedColors provides extended,
        LocalItmoShapes provides ItmoShapes.Default,
        LocalItmoSpacing provides ItmoSpacing.Default,
        LocalItmoEmphasizedTypography provides EmphasizedTypography,
        LocalItmoMotion provides ItmoMotion.Default,
        LocalMinimumInteractiveComponentSize provides ItmoSpacing.Default.touchTarget,
    ) {
        ItmoMaterialTheme(scheme, expressive) {
            // Text without a colour of its own reads onSurface, not Compose's default black, also outside a Surface.
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content)
        }
    }
}

/**
 * Sets the expressive switch for [content] inside an [ItmoTheme] and keeps its scheme and tokens; for previews and
 * tests that show both states side by side. Screens never call it: the switch is the theme's.
 */
@Composable
internal fun ProvideItmoExpressive(expressive: Boolean, content: @Composable () -> Unit) {
    ItmoMaterialTheme(MaterialTheme.colorScheme, expressive, content)
}

/**
 * Material's theme over the kit's tokens. Expressive or not, components animate with the standard motion scheme;
 * only a hero takes [ItmoTheme.heroMotionScheme].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ItmoMaterialTheme(scheme: ColorScheme, expressive: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalItmoExpressive provides expressive) {
        if (expressive) {
            MaterialExpressiveTheme(
                colorScheme = scheme,
                motionScheme = ItmoMotion.Default.scheme,
                shapes = MaterialShapes,
                typography = ItmoMaterialTypography,
                content = content,
            )
        } else {
            MaterialTheme(
                colorScheme = scheme,
                motionScheme = ItmoMotion.Default.scheme,
                shapes = MaterialShapes,
                typography = ItmoMaterialTypography,
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

    /**
     * The track of every progress bar, meter, scale and ring: the colour of the text around it (the container's "on"
     * colour in a card, `onBackground` on the screen) at a low alpha, so the track reads as a groove in its
     * container's hue in light and dark, never a dark bar on a light card.
     */
    val progressTrack: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalContentColor.current.copy(alpha = PROGRESS_TRACK_ALPHA)

    /** True when the kit's expressive variants are on: the default of [ItmoTheme]. */
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

private val MaterialShapes = ItmoShapes.Default.toMaterialShapes()
private val EmphasizedTypography = emphasizedTypographyOf(ItmoMaterialTypography)

/** The platform's wallpaper colours, or null where there are none (Android below 12). */
@Composable
internal expect fun platformWallpaperPalette(): WallpaperPalette?

private const val PROGRESS_TRACK_ALPHA = 0.16f
