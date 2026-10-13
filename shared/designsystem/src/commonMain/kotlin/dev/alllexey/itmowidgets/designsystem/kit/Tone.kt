package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * The semantic colour of a card, pill or icon tile. The word on it carries the meaning (`идёт`, `изменена`,
 * `записаны`); the colour only helps scanning, so a tone is never the only signal.
 */
enum class Tone { Neutral, Primary, Tertiary, Success, Warning, Error }

/** The container of [this] tone. */
@Composable
fun Tone.container(): Color = with(ItmoTheme.colorScheme) {
    val extended = ItmoTheme.extendedColors
    when (this@container) {
        Tone.Neutral -> surfaceContainerHigh
        Tone.Primary -> primaryContainer
        Tone.Tertiary -> tertiaryContainer
        Tone.Success -> extended.successContainer
        Tone.Warning -> extended.warningContainer
        Tone.Error -> errorContainer
    }
}

/** Text and icons on [container]. */
@Composable
fun Tone.onContainer(): Color = with(ItmoTheme.colorScheme) {
    val extended = ItmoTheme.extendedColors
    when (this@onContainer) {
        Tone.Neutral -> onSurfaceVariant
        Tone.Primary -> onPrimaryContainer
        Tone.Tertiary -> onTertiaryContainer
        Tone.Success -> extended.onSuccessContainer
        Tone.Warning -> extended.onWarningContainer
        Tone.Error -> onErrorContainer
    }
}

/**
 * True inside a sheet (`SheetScaffold` provides it): cards and lesson rows step one surface level up, so they stay
 * apart from the sheet, which is itself `surfaceContainerLow`.
 */
val LocalOnSheet = staticCompositionLocalOf { false }

/** The surface of a card or a lesson card: one level above what it stands on. */
@Composable
fun cardSurface(): Color =
    if (LocalOnSheet.current) ItmoTheme.colorScheme.surfaceContainerHigh else ItmoTheme.colorScheme.surfaceContainerLow

/** What stands under a card: the sheet's container or the screen's background, for rings cut out of it. */
@Composable
internal fun underSurface(): Color =
    if (LocalOnSheet.current) ItmoTheme.colorScheme.surfaceContainerLow else ItmoTheme.colorScheme.background
