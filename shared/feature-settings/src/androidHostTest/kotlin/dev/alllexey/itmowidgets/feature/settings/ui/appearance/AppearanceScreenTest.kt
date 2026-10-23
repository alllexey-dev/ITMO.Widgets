package dev.alllexey.itmowidgets.feature.settings.ui.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeMode
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.designsystem.components.controls.ColorPickerTags
import dev.alllexey.itmowidgets.designsystem.theme.ColorSource
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.appearance.AppearanceUiState
import dev.alllexey.itmowidgets.feature.settings.ui.preview.AppearanceSamples
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * «Оформление»: every choice is on the page and reports at once, the picker shows only with `Свой цвет`, the colours
 * stop reacting under `Монохром`, the sample is one picture. Without a size qualifier: under `w411dp-h891dp`
 * Robolectric never idles with the picker's text field.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AppearanceScreenTest {

    private val calls = mutableListOf<Any>()
    private val actions = AppearanceActions(
        onMode = { calls += it },
        onAccent = { calls += it },
        onCustomColor = { calls += it },
        onStyle = { calls += it },
        onContrast = { calls += it },
        onBlackBackground = { calls += it },
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: AppearanceUiState) = setContent {
        ItmoTheme(dark = false, colorSource = ColorSource.Theme(state.theme)) { AppearanceScreen(state, actions) }
    }

    @Test
    fun everyChoiceIsOnThePageAndReportsAtOnce() = runComposeUiTest {
        show(AppearanceSamples.state())

        onNodeWithTag(AppearanceTags.mode(ThemeMode.SYSTEM)).assertIsSelected().assert(hasText("Системная"))
        onNodeWithTag(AppearanceTags.mode(ThemeMode.DARK)).assertIsNotSelected().performClick()
        onNodeWithTag(AppearanceTags.accent(AccentColor.WALLPAPER)).assertIsSelected().assert(hasText("Как обои"))
        onNodeWithText("Цвет берётся из обоев телефона и меняется вместе с ними").assertExists()
        onNodeWithTag(AppearanceTags.PICKER).assertDoesNotExist()
        onNodeWithTag(AppearanceTags.accent(AccentColor.TEAL)).assertIsNotSelected().performScrollTo().performClick()
        onNodeWithTag(AppearanceTags.style(ThemeStyle.TONAL_SPOT)).assertIsSelected()
        onNodeWithText("Мягкие цвета, как в Android по умолчанию").assertExists()
        // A semantics click: the sideways row is out of Robolectric's small window, which a gesture cannot reach.
        onNodeWithTag(AppearanceTags.style(ThemeStyle.VIBRANT)).performSemanticsAction(SemanticsActions.OnClick)
        onNodeWithTag(AppearanceTags.contrast(ThemeContrast.STANDARD)).assertIsSelected()
        onNodeWithTag(AppearanceTags.contrast(ThemeContrast.HIGH)).performScrollTo().performClick()
        onNodeWithTag(AppearanceTags.BLACK).performScrollTo().assertIsOff().performClick()
        assertTouchTargets()

        assertEquals(listOf(ThemeMode.DARK, AccentColor.TEAL, ThemeStyle.VIBRANT, ThemeContrast.HIGH, true), calls)
    }

    @Test
    fun theOwnColourShowsItsPickerWhichTakesSixHexDigitsAndFlagsAnUnfinishedValue() = runComposeUiTest {
        show(AppearanceSamples.state(ThemeSpec(AccentColor.CUSTOM, customArgb = 0xFF5C6BC0.toInt())))

        onNodeWithTag(AppearanceTags.accent(AccentColor.CUSTOM)).assertIsSelected()
        listOf(ColorPickerTags.HUE, ColorPickerTags.SATURATION, ColorPickerTags.BRIGHTNESS).forEach { tag ->
            onNodeWithTag(tag).assert(hasContentDescription(LABELS.getValue(tag)))
        }
        val hex = onNodeWithTag(ColorPickerTags.HEX)
        hex.assert(hasText("#5C6BC0"))
        hex.performTextReplacement("#00897b")
        hex.assert(hasText("#00897B"))
        hex.performTextReplacement("#12")
        onNodeWithText(HEX_ERROR).assertDoesNotExist()
        hex.performImeAction()

        onNodeWithText(HEX_ERROR).assertExists()
        assertEquals(listOf<Any>(0xFF00897B.toInt()), calls)
    }

    @Test
    fun underMonochromeTheColoursSayWhyAndDoNothing() = runComposeUiTest {
        show(AppearanceSamples.state(AppearanceSamples.MONOCHROME))

        onNodeWithText("В палитре «Монохром» цвет не используется — выберите другую палитру").assertExists()
        onNodeWithTag(AppearanceTags.accent(AccentColor.BRAND)).performClick()
        assertEquals(emptyList<Any>(), calls)
    }

    @Test
    fun theSampleIsOnePictureInBothThemes() = runComposeUiTest {
        show(AppearanceSamples.state())

        onNodeWithTag(AppearanceTags.SAMPLE).assert(hasContentDescription("Пример оформления в светлой и тёмной теме"))
        onNodeWithTag(AppearanceTags.SAMPLE).assertHasNoClickAction()
        onNodeWithText("Записаться").assertDoesNotExist()
    }

    @Test
    fun aKeptThemeShowsItsSampleAloneAndTheLightOneDropsTheBlackBackground() = runComposeUiTest {
        var theme by mutableStateOf(AppearanceSamples.DARK)
        setContent { ItmoTheme(dark = false) { AppearanceScreen(AppearanceSamples.state(theme), actions) } }

        onNodeWithTag(AppearanceTags.SAMPLE).assert(hasContentDescription("Пример оформления в тёмной теме"))
        onNodeWithTag(AppearanceTags.mode(ThemeMode.DARK)).assertIsSelected()
        onNodeWithTag(AppearanceTags.BLACK).performScrollTo().assertExists()

        theme = theme.copy(mode = ThemeMode.LIGHT)
        onNodeWithTag(AppearanceTags.SAMPLE).assert(hasContentDescription("Пример оформления в светлой теме"))
        onNodeWithTag(AppearanceTags.BLACK).assertDoesNotExist()
    }

    @Test
    fun whereTheAppKeepsTheSystemColoursThereIsNoBlackBackgroundAndTheNoteSaysSo() = runComposeUiTest {
        show(AppearanceSamples.state(ios = true))

        onNodeWithTag(AppearanceTags.BLACK).assertDoesNotExist()
        onNodeWithTag(AppearanceTags.accent(AccentColor.WALLPAPER)).assertDoesNotExist()
        onNodeWithTag(AppearanceTags.accent(AccentColor.BRAND)).assertIsSelected()
        onNodeWithText("На iPhone приложение остаётся в системных цветах", substring = true).performScrollTo()
    }

    private companion object {
        const val HEX_ERROR = "Шесть знаков 0–9 и A–F, например #4984E2"
        val LABELS = mapOf(
            ColorPickerTags.HUE to "Оттенок",
            ColorPickerTags.SATURATION to "Насыщенность",
            ColorPickerTags.BRIGHTNESS to "Яркость",
        )
    }
}
