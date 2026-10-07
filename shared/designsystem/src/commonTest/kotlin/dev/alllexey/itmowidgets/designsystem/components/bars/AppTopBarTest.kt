package dev.alllexey.itmowidgets.designsystem.components.bars

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithContentDescription
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
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AppTopBarTest {
    @Test
    fun navigationAndActionsAreLabelledTargets() = runComposeUiTest {
        val clicks = mutableListOf<String>()
        setContent {
            ItmoTheme {
                AppTopBar(
                    title = TITLE,
                    navigation = { AppTopBarAction(Icon, BACK, onClick = { clicks += BACK }) },
                    actions = { AppTopBarAction(Icon, SEARCH, onClick = { clicks += SEARCH }) },
                )
            }
        }

        onNodeWithContentDescription(BACK).performClick()
        onNodeWithContentDescription(SEARCH).performClick()

        assertEquals(listOf(BACK, SEARCH), clicks)
        assertTouchTargets()
        assertNoTextOverflow()
    }

    @Test
    fun titleIsAHeading() = runComposeUiTest {
        setContent { ItmoTheme { AppTopBar(title = TITLE) } }

        onNodeWithText(TITLE).assertIsDisplayed().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun theIosBarKeepsLabelledTargetsAndAHeadingAndCentresTheTitle() = runComposeUiTest {
        val clicks = mutableListOf<String>()
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Ios) {
                AppTopBar(
                    title = TITLE,
                    modifier = Modifier.testTag(BAR),
                    navigation = { AppTopBarBack(BACK, onClick = { clicks += BACK }, title = PREVIOUS) },
                    actions = {
                        AppTopBarAction(Icon, SEARCH, onClick = { clicks += SEARCH })
                        AppTopBarTextAction(DONE, onClick = { clicks += DONE })
                    },
                )
            }
        }

        onNodeWithContentDescription(BACK).performClick()
        onNodeWithContentDescription(SEARCH).performClick()
        onNodeWithText(DONE).performClick()

        assertEquals(listOf(BACK, SEARCH, DONE), clicks)
        onNodeWithText(PREVIOUS).assertDoesNotExist()
        assertTouchTargets(ItmoPlatformStyle.Ios.minTouchTarget)
        val bar = onNodeWithTag(BAR).getBoundsInRoot()
        val title = onNodeWithText(TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .getBoundsInRoot()
        (bar.bottom - bar.top).assertIsEqualTo(IosMetrics.navigationBarHeight, "bar height")
        ((title.left + title.right) / 2).assertIsEqualTo((bar.left + bar.right) / 2, "title centre", Tolerance)
    }

    @Test
    fun theMaterialBackIsTheBackArrowAction() = runComposeUiTest {
        setContent { ItmoTheme { AppTopBar(title = TITLE, navigation = { AppTopBarBack(BACK, onClick = {}, title = PREVIOUS) }) } }

        onNodeWithContentDescription(BACK).assertIsDisplayed()
        onNodeWithText(PREVIOUS).assertDoesNotExist()
        assertTouchTargets()
    }

    @Test
    fun theSubtitleIsOneLineUnderAOneLineHeadingInEveryStyle() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        setContent {
            ItmoTheme(platformStyle = style) {
                AppTopBar(
                    title = LONG,
                    subtitle = LONG_HOST,
                    navigation = { AppTopBarAction(Icon, BACK, onClick = {}) },
                )
            }
        }

        for (value in ItmoPlatformStyle.entries) {
            style = value
            waitForIdle()
            val title = onNodeWithText(LONG).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            val host = onNodeWithText(LONG_HOST).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
            assertEquals(1, title.lineCount(), "$value title lines")
            assertEquals(1, host.lineCount(), "$value subtitle lines")
            assertTrue(host.getBoundsInRoot().top >= title.getBoundsInRoot().bottom, "$value subtitle under the title")
        }
    }

    @Test
    fun withoutASubtitleTheMaterialTitleStillTakesTwoLines() = runComposeUiTest {
        setContent { ItmoTheme { AppTopBar(title = LONG, navigation = { AppTopBarAction(Icon, BACK, onClick = {}) }) } }

        assertEquals(2, onNodeWithText(LONG).lineCount())
    }

    private fun SemanticsNodeInteraction.lineCount(): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().lineCount
    }

    private companion object {
        const val TITLE = "Друзья"
        const val BACK = "Назад"
        const val SEARCH = "Найти людей"
        const val DONE = "Готово"
        const val PREVIOUS = "Расписание"
        const val LONG = "Математический анализ и дифференциальные уравнения в частных производных"
        const val LONG_HOST = "очень-длинное-имя-узла.подразделение.университет-итмо.example.ru"
        const val BAR = "bar"
        val Icon = ColorPainter(Color.Black)
        val Tolerance = 1.dp
    }
}
