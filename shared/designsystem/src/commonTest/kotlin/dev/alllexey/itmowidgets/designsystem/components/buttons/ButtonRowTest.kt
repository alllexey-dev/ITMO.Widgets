package dev.alllexey.itmowidgets.designsystem.components.buttons

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ButtonRowTest {
    @Test
    fun buttonsShareARowWhenTheyFit() = runComposeUiTest {
        setContent { Answer(widthDp = 411, fontScale = 1f) }

        val accept = onNodeWithText(ACCEPT).getBoundsInRoot()
        val decline = onNodeWithText(DECLINE).getBoundsInRoot()

        assertEquals(accept.top, decline.top)
        assertTrue(accept.right < decline.left)
        assertTouchTargets()
    }

    @Test
    fun buttonsStackAtFullWidthAtLargeFontOnANarrowScreen() = runComposeUiTest {
        setContent { Answer(widthDp = 320, fontScale = 1.3f) }

        val accept = onNodeWithText(ACCEPT).getBoundsInRoot()
        val decline = onNodeWithText(DECLINE).getBoundsInRoot()

        assertTrue(decline.top > accept.bottom, "stacked: $accept above $decline")
        listOf(ACCEPT, DECLINE).forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            assertEquals(1, layouts.single().lineCount, "$label keeps one line")
        }
        assertTouchTargets()
    }

    @Test
    fun iosButtonsStackByTheSameRule() = runComposeUiTest {
        setContent { Answer(widthDp = 320, fontScale = 1.3f, style = ItmoPlatformStyle.Ios) }

        val accept = onNodeWithText(ACCEPT).getBoundsInRoot()
        val decline = onNodeWithText(DECLINE).getBoundsInRoot()

        assertTrue(decline.top > accept.bottom, "stacked: $accept above $decline")
        assertTouchTargets(ItmoPlatformStyle.Ios.minTouchTarget)
    }

    @Test
    fun iosButtonsShareARowWhenTheyFit() = runComposeUiTest {
        setContent { Answer(widthDp = 411, fontScale = 1f, style = ItmoPlatformStyle.Ios) }

        val accept = onNodeWithText(ACCEPT).getBoundsInRoot()
        val decline = onNodeWithText(DECLINE).getBoundsInRoot()

        assertEquals(accept.top, decline.top)
        assertTrue(accept.right < decline.left)
    }

    @Composable
    private fun Answer(widthDp: Int, fontScale: Float, style: ItmoPlatformStyle = ItmoPlatformStyle.Material) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            ItmoTheme(platformStyle = style) {
                ButtonRow(Modifier.width((widthDp - 2 * CARD_INSET_DP).dp)) {
                    ProgressButton(ACCEPT, onClick = {})
                    ProgressButton(DECLINE, onClick = {}, style = ProgressButtonStyle.Tonal)
                }
            }
        }
    }

    private companion object {
        const val ACCEPT = "Принять заявку"
        const val DECLINE = "Отклонить"

        /** The profile card's screen margin and padding on each side. */
        const val CARD_INSET_DP = 32
    }
}
