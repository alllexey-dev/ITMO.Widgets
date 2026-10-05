package dev.alllexey.itmowidgets.designsystem.components.state

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ContentStateTest {
    @Test
    fun actionFiresAndLongTextWraps() = runComposeUiTest {
        var retries = 0
        setContent {
            ItmoTheme {
                ContentState(
                    title = LONG_TITLE,
                    icon = ColorPainter(Color.Black),
                    description = LONG_DESCRIPTION,
                    action = ContentStateAction(RETRY, onClick = { retries++ }),
                )
            }
        }

        onNodeWithText(RETRY).performClick()

        assertEquals(1, retries)
        assertTouchTargets()
        assertNoTextOverflow()
    }

    @Test
    fun iconIsDecorative() = runComposeUiTest {
        setContent {
            ItmoTheme {
                ContentState(title = LONG_TITLE, size = ContentStateSize.Compact, icon = ColorPainter(Color.Black))
            }
        }

        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertCountEquals(0)
    }

    @Test
    fun loadingIsAnIndeterminateIndicator() = runComposeUiTest {
        setContent { ItmoTheme { ContentStateLoading(size = ContentStateSize.Compact) } }

        onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    private companion object {
        const val LONG_TITLE = "Математический анализ и дифференциальные уравнения в частных производных"
        const val LONG_DESCRIPTION = "Преображенская Александра Вячеславовна закрыла расписание от всех, кроме друзей"
        const val RETRY = "Повторить"
    }
}
