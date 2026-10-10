package dev.alllexey.itmowidgets.designsystem.components.dialogs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

/** The colour picker in both platform styles: named radio swatches that report a pick and stay open. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SwatchChoiceDialogTest {

    @Test
    fun swatchesAreNamedRadioButtonsThatReportAPickAndKeepTheDialog() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        var selected by mutableStateOf(1)
        val picks = mutableListOf<Int>()
        var dismissals = 0
        setContent {
            ItmoTheme(platformStyle = style) {
                SwatchChoiceDialog(
                    title = TITLE,
                    swatches = SWATCHES,
                    selectedIndex = selected,
                    onSelect = { picks += it; selected = it },
                    onDismiss = { dismissals++ },
                    confirmLabel = DONE,
                )
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            selected = 1
            onNodeWithContentDescription(SWATCHES[1].label).assertIsSelected()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            onNodeWithText(SWATCHES[1].label).assertIsDisplayed()
            onNodeWithContentDescription(SWATCHES[2].label).assertIsNotSelected().performClick()
            onNodeWithContentDescription(SWATCHES[2].label).assertIsSelected()
            onNodeWithText(SWATCHES[2].label).assertIsDisplayed()
            onNodeWithText(TITLE).assertIsDisplayed()
            assertTouchTargets(it.minTouchTarget)
            onNodeWithText(DONE).performClick()
        }

        assertEquals(listOf(2, 2), picks)
        assertEquals(2, dismissals)
    }

    private companion object {
        const val TITLE = "Цвет оформления"
        const val DONE = "Готово"
        val SWATCHES = listOf(
            Swatch("Фирменный", Color(0xFF435E91), Color.White),
            Swatch("Бирюзовый", Color(0xFF006A60), Color.White),
            Swatch("Фиолетовый", Color(0xFF7B4E7F), Color.White),
            Swatch("Янтарный", Color(0xFF7D5800), Color.White),
            Swatch("Красный", Color(0xFF904A43), Color.White),
        )
    }
}
