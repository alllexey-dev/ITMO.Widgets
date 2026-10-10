package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.designsystem.components.controls.ColorPickerTags
import dev.alllexey.itmowidgets.designsystem.theme.ColorSource
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.ui.preview.SettingsPreviewData
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The custom colour of the accent dialog (DS-ACC2). Its own class without `SettingsScreenTest`'s `w411dp-h891dp`: under
 * that qualifier Robolectric never idles with a text field in a dialog window.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class CustomColorPickerTest {

    private val choices = mutableListOf<Pair<SettingRowId, String>>()

    @Test
    fun theCustomColourShowsItsPickerTakesSixHexDigitsAtOnceAndFlagsAnUnfinishedValue() = runComposeUiTest {
        val state = SettingsPreviewData.appearance(ThemeSpec(AccentColor.CUSTOM, customArgb = 0xFF5C6BC0.toInt()))
        setContent {
            ItmoTheme(dark = false, colorSource = ColorSource.Theme(state.themePreview!!)) {
                SettingsScreen(state, SettingsActions(onChoice = { id, key -> choices += id to key }), widgetPreview = {})
            }
        }
        onNodeWithTag(SettingRowId.ACCENT_CUSTOM.key).assert(hasText("#5C6BC0")).performClick()

        onNode(
            hasContentDescription("Свой цвет") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton) and
                hasAnyAncestor(isDialog()),
        ).assertIsSelected()
        listOf(ColorPickerTags.HUE, ColorPickerTags.SATURATION, ColorPickerTags.BRIGHTNESS).forEach { tag ->
            onNodeWithTag(tag).assert(hasContentDescription(LABELS.getValue(tag)))
        }
        assertTouchTargets()
        val hex = onNodeWithTag(ColorPickerTags.HEX)
        hex.assert(hasText("#5C6BC0"))
        hex.performTextReplacement("#00897b")
        hex.assert(hasText("#00897B"))
        hex.performTextReplacement("00897b4")
        hex.assert(hasText("00897B"))
        hex.performTextReplacement("#12")
        onNodeWithText(HEX_ERROR).assertDoesNotExist()
        hex.performImeAction()

        onNodeWithText(HEX_ERROR).assertExists()
        onNode(isDialog()).assertExists()
        assertEquals(List(2) { SettingRowId.ACCENT_CUSTOM to "#00897B" }, choices)
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
