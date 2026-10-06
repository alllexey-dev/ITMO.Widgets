package dev.alllexey.itmowidgets.designsystem.components.bars

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.SemanticsProperties
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

    private companion object {
        const val TITLE = "Друзья"
        const val BACK = "Назад"
        const val SEARCH = "Найти людей"
        const val DONE = "Готово"
        const val PREVIOUS = "Расписание"
        const val BAR = "bar"
        val Icon = ColorPainter(Color.Black)
        val Tolerance = 1.dp
    }
}
