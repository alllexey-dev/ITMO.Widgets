package dev.alllexey.itmowidgets.designsystem.components.state

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
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
    fun iosActionIsACapsuleButtonThatFiresAndLoadingIsTheLargeSpinner() = runComposeUiTest {
        var retries = 0
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Ios) {
                Column {
                    ContentState(
                        title = LONG_TITLE,
                        size = ContentStateSize.Compact,
                        action = ContentStateAction(RETRY, onClick = { retries++ }),
                    )
                    ContentStateLoading(size = ContentStateSize.Compact)
                }
            }
        }

        onNodeWithText(RETRY).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()

        assertEquals(1, retries)
        assertTouchTargets(ItmoPlatformStyle.Ios.minTouchTarget)
        onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertWidthIsEqualTo(IosMetrics.activityIndicatorLarge)
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
    fun theActionModifierGoesOnTheButtonInEveryStyle() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        var retries = 0
        setContent {
            ItmoTheme(platformStyle = style) {
                ContentState(
                    title = LONG_TITLE,
                    action = ContentStateAction(RETRY, onClick = { retries++ }, modifier = Modifier.testTag(ACTION)),
                )
            }
        }

        for (value in ItmoPlatformStyle.entries) {
            style = value
            waitForIdle()
            onNodeWithTag(ACTION)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(hasText(RETRY))
                .performClick()
        }

        assertEquals(ItmoPlatformStyle.entries.size, retries)
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
        const val ACTION = "state_action"
    }
}
