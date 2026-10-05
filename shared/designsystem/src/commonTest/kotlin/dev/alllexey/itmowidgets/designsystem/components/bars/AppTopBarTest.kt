package dev.alllexey.itmowidgets.designsystem.components.bars

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
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

    private companion object {
        const val TITLE = "Друзья"
        const val BACK = "Назад"
        const val SEARCH = "Найти людей"
        val Icon = ColorPainter(Color.Black)
    }
}
