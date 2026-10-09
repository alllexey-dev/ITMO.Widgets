package dev.alllexey.itmowidgets.designsystem.components.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoNavigationBarTest {
    @Test
    fun tabsAreNamedSelectableTargetsAndEveryOneShowsItsLabel() = runComposeUiTest {
        var selected by mutableIntStateOf(0)
        setContent {
            ItmoTheme {
                ItmoNavigationBar(Modifier.testTag(BAR)) {
                    LABELS.forEachIndexed { index, label ->
                        ItmoNavigationBarItem(
                            selected = index == selected,
                            onClick = { selected = index },
                            label = label,
                            icon = ColorPainter(Color.Gray),
                            selectedIcon = ColorPainter(Color.Black),
                        )
                    }
                }
            }
        }

        onNodeWithContentDescription(SPORT).performClick()

        onNodeWithContentDescription(SPORT).assertIsSelected()
        onNodeWithContentDescription(HOME).assertIsNotSelected()
        onNodeWithText(SPORT, useUnmergedTree = true).assertExists()
        onNodeWithText(HOME, useUnmergedTree = true).assertExists()
        assertTouchTargets()
        assertTrue(onNodeWithTag(BAR).getBoundsInRoot().height >= NavigationBarTokens.MinHeight)
    }

    private companion object {
        const val BAR = "bar"
        const val HOME = "Главная"
        const val SPORT = "Спорт"
        val LABELS = listOf("Зачётка", "Расписание", HOME, SPORT, "Профиль")
    }
}
