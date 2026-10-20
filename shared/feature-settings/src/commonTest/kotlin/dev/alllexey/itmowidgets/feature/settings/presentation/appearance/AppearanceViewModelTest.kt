package dev.alllexey.itmowidgets.feature.settings.presentation.appearance

import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.presentation.EveryPlatformCapability
import dev.alllexey.itmowidgets.feature.settings.presentation.FakeSettingsRepository
import dev.alllexey.itmowidgets.feature.settings.presentation.FakeWidgetRefreshRequester
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** «Оформление»: the stored [ThemeSpec] as one choice per part, the notes that say what a choice does. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private class Fixture(theme: ThemeSpec, capabilities: PlatformCapabilities) {
        val repository = FakeSettingsRepository(LocalSettings(theme = theme), SharingSettingsState.Disabled, true, MutableStateFlow(true))
        val refresher = FakeWidgetRefreshRequester()
        val viewModel = AppearanceViewModel(repository, refresher, capabilities)
        val state get() = viewModel.uiState.value
    }

    private fun fixture(theme: ThemeSpec = ThemeSpec(), capabilities: PlatformCapabilities = EveryPlatformCapability) =
        Fixture(theme, capabilities)

    @Test
    fun theDefaultsAreTheWallpaperWithItsNoteEveryPaletteAndTheBlackBackgroundOff() = runTest(main.dispatcher) {
        val fixture = fixture()
        assertFalse(fixture.state.loaded)
        advanceUntilIdle()

        val state = fixture.state
        assertTrue(state.loaded)
        assertEquals(ThemeSpec(), state.theme)
        assertEquals(AccentColor.entries, state.accents.map { it.accent })
        assertEquals(UiText.Res(SettingsStrings.settings_accent_color_wallpaper), state.accents.first().label)
        assertEquals(AccentColor.WALLPAPER, state.accent)
        assertTrue(state.accentApplies)
        assertEquals(UiText.Res(SettingsStrings.settings_appearance_wallpaper_note), state.accentNote)
        assertNull(state.customArgb)
        assertEquals(ThemeStyle.entries, state.styles.map { it.style })
        assertEquals(UiText.Res(SettingsStrings.settings_theme_style_tonal_spot_description), state.style?.description)
        assertEquals(ThemeContrast.entries, state.contrasts.map { it.contrast })
        assertEquals(false, state.blackBackground)
        assertEquals(UiText.Res(SettingsStrings.settings_appearance_widgets_note), state.footer)
    }

    @Test
    fun withoutWallpaperColoursAndAppColoursTheDefaultIsTheBrandAndOnlyTheWidgetsTakeIt() = runTest(main.dispatcher) {
        // iOS: the app keeps the system's colours, so the black background would change nothing.
        val fixture = fixture(capabilities = EveryPlatformCapability.copy(wallpaperColors = false, appColors = false))
        advanceUntilIdle()

        val state = fixture.state
        assertEquals(AccentColor.entries - AccentColor.WALLPAPER, state.accents.map { it.accent })
        assertEquals(AccentColor.BRAND, state.accent)
        assertNull(state.accentNote)
        assertNull(state.blackBackground)
        assertEquals(UiText.Res(SettingsStrings.settings_appearance_widgets_only_note), state.footer)
    }

    @Test
    fun aColourAppliesAtOnceAndTheWidgetsRedraw() = runTest(main.dispatcher) {
        val fixture = fixture()
        advanceUntilIdle()

        fixture.viewModel.selectAccent(AccentColor.TEAL)
        advanceUntilIdle()

        assertEquals(listOf(ThemeSpec(accent = AccentColor.TEAL)), fixture.repository.themeRequests)
        assertEquals(AccentColor.TEAL, fixture.state.accent)
        assertNull(fixture.state.accentNote)
        assertEquals(1, fixture.refresher.refreshCount)
    }

    @Test
    fun theOwnColourBecomesTheAccentOpaqueAndStaysWhileAnotherIsPicked() = runTest(main.dispatcher) {
        val fixture = fixture()
        advanceUntilIdle()

        fixture.viewModel.setCustomColor(0x005C6BC0)
        advanceUntilIdle()
        assertEquals(ThemeSpec(accent = AccentColor.CUSTOM, customArgb = 0xFF5C6BC0.toInt()), fixture.repository.themeRequests.last())
        assertEquals(0xFF5C6BC0.toInt(), fixture.state.customArgb)

        fixture.viewModel.selectAccent(AccentColor.BRAND)
        advanceUntilIdle()
        assertNull(fixture.state.customArgb)
        assertEquals(0xFF5C6BC0.toInt(), fixture.repository.themeRequests.last().customArgb)
    }

    @Test
    fun underMonochromeTheColoursAreOffWithTheNoteWhy() = runTest(main.dispatcher) {
        val fixture = fixture(ThemeSpec(accent = AccentColor.PURPLE))
        advanceUntilIdle()

        fixture.viewModel.selectStyle(ThemeStyle.MONOCHROME)
        advanceUntilIdle()

        assertFalse(fixture.state.accentApplies)
        assertEquals(UiText.Res(SettingsStrings.settings_appearance_monochrome_note), fixture.state.accentNote)
        // The colour is kept for when another palette comes back.
        assertEquals(AccentColor.PURPLE, fixture.state.accent)
    }

    @Test
    fun paletteContrastAndTheBlackBackgroundKeepTheOtherChoices() = runTest(main.dispatcher) {
        val fixture = fixture(ThemeSpec(accent = AccentColor.PURPLE))
        advanceUntilIdle()

        fixture.viewModel.selectStyle(ThemeStyle.VIBRANT)
        fixture.viewModel.selectContrast(ThemeContrast.HIGH)
        fixture.viewModel.setBlackBackground(true)
        advanceUntilIdle()

        val stored = ThemeSpec(AccentColor.PURPLE, style = ThemeStyle.VIBRANT, contrast = ThemeContrast.HIGH, pureBlack = true)
        assertEquals(stored, fixture.repository.themeRequests.last())
        assertEquals(3, fixture.refresher.refreshCount)
        assertEquals(stored, fixture.state.theme)
        assertEquals(UiText.Res(SettingsStrings.settings_theme_style_vibrant_description), fixture.state.style?.description)
        assertEquals(true, fixture.state.blackBackground)
    }

    @Test
    fun theIosColoursKeepTheHueUnderMonochromeAndFollowThePalette() {
        val teal = ThemeSpec(accent = AccentColor.TEAL)
        val mono = teal.copy(style = ThemeStyle.MONOCHROME)
        assertEquals(AppearanceColors.swatch(teal, dark = false), AppearanceColors.swatch(mono, dark = false))
        assertEquals(AppearanceColors.roles(teal, dark = true).primary, AppearanceColors.palette(teal, ThemeStyle.TONAL_SPOT, dark = true).first())
        assertTrue(AppearanceColors.palette(teal, ThemeStyle.MONOCHROME, dark = false) != AppearanceColors.palette(teal, ThemeStyle.VIBRANT, dark = false))
    }
}
