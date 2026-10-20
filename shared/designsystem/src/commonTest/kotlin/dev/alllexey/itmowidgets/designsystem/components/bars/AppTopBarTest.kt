package dev.alllexey.itmowidgets.designsystem.components.bars

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun theBackIsTheBackArrowAction() = runComposeUiTest {
        setContent {
            ItmoTheme {
                AppTopBar(title = TITLE, navigation = { AppTopBarBack(BACK, onClick = {}) })
            }
        }

        onNodeWithContentDescription(BACK).assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun theSubtitleIsOneLineUnderAOneLineHeading() = runComposeUiTest {
        setContent {
            ItmoTheme {
                AppTopBar(
                    title = LONG,
                    subtitle = LONG_HOST,
                    navigation = { AppTopBarAction(Icon, BACK, onClick = {}) },
                )
            }
        }

        val title = onNodeWithText(LONG).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        val host = onNodeWithText(LONG_HOST).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
        assertEquals(1, title.lineCount(), "title lines")
        assertEquals(1, host.lineCount(), "subtitle lines")
        assertTrue(host.getBoundsInRoot().top >= title.getBoundsInRoot().bottom, "subtitle under the title")
    }

    @Test
    fun withoutASubtitleTheMaterialTitleStillTakesTwoLines() = runComposeUiTest {
        // Phone-wide on every platform: a simulator test runs in a 1024 pt window, where even this title fits one line
        // (Robolectric's is 320 dp).
        setContent {
            ItmoTheme {
                AppTopBar(
                    title = LONG,
                    modifier = Modifier.width(PhoneWidth),
                    navigation = { AppTopBarAction(Icon, BACK, onClick = {}) },
                )
            }
        }

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
        const val LONG = "Математический анализ и дифференциальные уравнения в частных производных"
        const val LONG_HOST = "очень-длинное-имя-узла.подразделение.университет-итмо.example.ru"
        val Icon = ColorPainter(Color.Black)
        val PhoneWidth = 320.dp
    }
}
