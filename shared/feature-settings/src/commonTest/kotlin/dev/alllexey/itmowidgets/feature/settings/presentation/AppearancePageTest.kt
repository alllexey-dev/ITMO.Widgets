package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_brand
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_teal
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_wallpaper
import dev.alllexey.itmowidgets.shared.feature.settings.settings_appearance_wallpaper_footer
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_contrast_standard
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_tonal_spot
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_vibrant_description
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** The appearance page of [RootPageProvider] (DS-ACC1, DS-ACC2): every row is a part of the stored [ThemeSpec]. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearancePageTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun theDefaultsAreTodaysLookWithTheSampleAndTheWallpaperNote() = runTest(main.dispatcher) {
        val fixture = createFixture(page = SettingsPage.APPEARANCE)
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(ThemeSpec(), state.themePreview)
        assertEquals(
            listOf(SettingRowId.ACCENT_COLOR, SettingRowId.THEME_PALETTE, SettingRowId.THEME_CONTRAST, SettingRowId.DARK_BLACK),
            fixture.viewModel.allItems().map { it.id },
        )
        val accent = fixture.viewModel.choice(SettingRowId.ACCENT_COLOR)
        assertEquals(AccentColor.entries.map { it.name }, accent.options.map { it.key })
        assertEquals(AccentColor.WALLPAPER.name, accent.selectedOptionKey)
        assertEquals(UiText.Res(Res.string.settings_accent_color_wallpaper), accent.value)
        assertEquals(UiText.Res(Res.string.settings_theme_style_tonal_spot), fixture.viewModel.choice(SettingRowId.THEME_PALETTE).value)
        assertEquals(
            UiText.Res(Res.string.settings_theme_contrast_standard),
            fixture.viewModel.choice(SettingRowId.THEME_CONTRAST).value,
        )
        assertFalse(fixture.viewModel.toggle(SettingRowId.DARK_BLACK).checked)
        assertEquals(UiText.Res(Res.string.settings_appearance_wallpaper_footer), state.sections.first().footer)
    }

    @Test
    fun withoutWallpaperColoursTheDefaultShowsAsTheBrandSchemeAndNoNote() = runTest(main.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.APPEARANCE,
            capabilities = EveryPlatformCapability.copy(wallpaperColors = false),
        )
        advanceUntilIdle()

        val choice = fixture.viewModel.choice(SettingRowId.ACCENT_COLOR)
        assertEquals(AccentColor.entries.drop(1).map { it.name }, choice.options.map { it.key })
        assertEquals(AccentColor.BRAND.name, choice.selectedOptionKey)
        assertEquals(UiText.Res(Res.string.settings_accent_color_brand), choice.value)
        assertNull(fixture.viewModel.uiState.value.sections.first().footer)
    }

    @Test
    fun pickingAColourStoresItAndTheRowFollows() = runTest(main.dispatcher) {
        val fixture = createFixture(page = SettingsPage.APPEARANCE)
        advanceUntilIdle()

        fixture.viewModel.onChoiceChanged(SettingRowId.ACCENT_COLOR, AccentColor.TEAL.name)
        fixture.viewModel.onChoiceChanged(SettingRowId.ACCENT_COLOR, "NOT_A_COLOUR")
        advanceUntilIdle()

        assertEquals(listOf(ThemeSpec(accent = AccentColor.TEAL)), fixture.repository.themeRequests)
        val choice = fixture.viewModel.choice(SettingRowId.ACCENT_COLOR)
        assertEquals(AccentColor.TEAL.name, choice.selectedOptionKey)
        assertEquals(UiText.Res(Res.string.settings_accent_color_teal), choice.value)
        assertNull(fixture.viewModel.uiState.value.sections.first().footer)
        // Widgets that follow the theme redraw in the new colour.
        assertEquals(1, fixture.widgetRefresher.refreshCount)
    }

    @Test
    fun theCustomColourRowShowsOnlyWhileItIsTheAccentAndTakesValidHex() = runTest(main.dispatcher) {
        val fixture = createFixture(page = SettingsPage.APPEARANCE)
        advanceUntilIdle()
        assertEquals(emptyList(), fixture.viewModel.allItems().filterIsInstance<SettingItem.CustomColor>())

        fixture.viewModel.onChoiceChanged(SettingRowId.ACCENT_CUSTOM, "#5c6bc0")
        fixture.viewModel.onChoiceChanged(SettingRowId.ACCENT_CUSTOM, "#5C6BC")
        advanceUntilIdle()

        assertEquals(
            listOf(ThemeSpec(accent = AccentColor.CUSTOM, customArgb = 0xFF5C6BC0.toInt())),
            fixture.repository.themeRequests,
        )
        val row = fixture.viewModel.allItems().filterIsInstance<SettingItem.CustomColor>().single()
        assertEquals(SettingRowId.ACCENT_CUSTOM, row.id)
        assertEquals(0xFF5C6BC0.toInt(), row.argb)
        assertEquals(AccentColor.CUSTOM.name, fixture.viewModel.choice(SettingRowId.ACCENT_COLOR).selectedOptionKey)

        fixture.viewModel.onChoiceChanged(SettingRowId.ACCENT_COLOR, AccentColor.BRAND.name)
        advanceUntilIdle()
        assertEquals(emptyList(), fixture.viewModel.allItems().filterIsInstance<SettingItem.CustomColor>())
        assertEquals(0xFF5C6BC0.toInt(), fixture.repository.themeRequests.last().customArgb)
    }

    @Test
    fun styleContrastAndTheBlackBackgroundKeepTheOtherChoices() = runTest(main.dispatcher) {
        val start = ThemeSpec(accent = AccentColor.PURPLE)
        val fixture = createFixture(local = LocalSettings(theme = start), page = SettingsPage.APPEARANCE)
        advanceUntilIdle()

        val styles = fixture.viewModel.choice(SettingRowId.THEME_PALETTE)
        assertEquals(ThemeStyle.entries.map { it.name }, styles.options.map { it.key })
        assertEquals(
            UiText.Res(Res.string.settings_theme_style_vibrant_description),
            styles.options.single { it.key == ThemeStyle.VIBRANT.name }.description,
        )
        assertEquals(ThemeContrast.entries.map { it.name }, fixture.viewModel.choice(SettingRowId.THEME_CONTRAST).options.map { it.key })

        fixture.viewModel.onChoiceChanged(SettingRowId.THEME_PALETTE, ThemeStyle.MONOCHROME.name)
        fixture.viewModel.onChoiceChanged(SettingRowId.THEME_CONTRAST, ThemeContrast.HIGH.name)
        fixture.viewModel.onToggleChanged(SettingRowId.DARK_BLACK, true)
        fixture.viewModel.onChoiceChanged(SettingRowId.THEME_PALETTE, "RAINBOW")
        advanceUntilIdle()

        val stored = ThemeSpec(AccentColor.PURPLE, style = ThemeStyle.MONOCHROME, contrast = ThemeContrast.HIGH, pureBlack = true)
        assertEquals(stored, fixture.repository.themeRequests.last())
        assertEquals(3, fixture.repository.themeRequests.size)
        assertEquals(3, fixture.widgetRefresher.refreshCount)
        assertEquals(stored, fixture.viewModel.uiState.value.themePreview)
        assertEquals(ThemeStyle.MONOCHROME.name, fixture.viewModel.choice(SettingRowId.THEME_PALETTE).selectedOptionKey)
        assertEquals(true, fixture.viewModel.toggle(SettingRowId.DARK_BLACK).checked)
    }
}
