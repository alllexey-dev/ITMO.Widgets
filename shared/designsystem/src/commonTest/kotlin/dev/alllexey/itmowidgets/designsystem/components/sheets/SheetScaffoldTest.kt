package dev.alllexey.itmowidgets.designsystem.components.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
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
class SheetScaffoldTest {
    @Test
    fun closeIsALabelledTargetInBothPlacements() = runComposeUiTest {
        var closes = 0
        setContent {
            ItmoTheme {
                Column {
                    SheetClosePlacement.entries.forEach { placement ->
                        SheetScaffold(
                            title = TITLE,
                            close = SheetClose("$CLOSE $placement", onClick = { closes++ }),
                            closePlacement = placement,
                        ) {}
                    }
                }
            }
        }

        SheetClosePlacement.entries.forEach { onNodeWithContentDescription("$CLOSE $it").performClick() }

        assertEquals(2, closes)
        assertTouchTargets()
        assertNoTextOverflow()
    }

    @Test
    fun titleIsAHeading() = runComposeUiTest {
        setContent { ItmoTheme { SheetScaffold(title = TITLE, subtitle = SUBTITLE) {} } }

        onNodeWithText(TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        onNodeWithText(SUBTITLE).assertIsDisplayed()
    }

    @Test
    fun aLongListScrollsInsideTheBodyAndTheFooterStaysOnScreen() = runComposeUiTest {
        setContent {
            ItmoTheme {
                SheetScaffold(
                    title = TITLE,
                    modifier = Modifier.testTag(SHEET).height(SHEET_HEIGHT),
                    footer = { Text(FOOTER) },
                ) {
                    LazyColumn { items(ROWS) { Box(Modifier.fillMaxWidth().height(48.dp)) } }
                }
            }
        }

        val sheet = onNodeWithTag(SHEET).getBoundsInRoot()
        val footer = onNodeWithText(FOOTER).assertIsDisplayed().getBoundsInRoot()
        assertEquals(SHEET_HEIGHT, sheet.bottom - sheet.top)
        assertTrue(footer.bottom <= sheet.bottom)
    }

    @Test
    fun aBoundedSheetIsAsTallAsTheMinimumBodyNeeds() = runComposeUiTest {
        setContent {
            ItmoTheme {
                SheetScaffold(title = TITLE, modifier = Modifier.testTag(SHEET), contentMinHeight = 288.dp) {}
            }
        }

        onNodeWithTag(SHEET).assertHeightIsAtLeast(288.dp)
    }

    private companion object {
        const val TITLE = "Ссылки"
        const val SUBTITLE = "Математический анализ"
        const val CLOSE = "Закрыть"
        const val FOOTER = "Добавить ссылку"
        const val SHEET = "sheet"
        const val ROWS = 40
        val SHEET_HEIGHT = 400.dp
    }
}
