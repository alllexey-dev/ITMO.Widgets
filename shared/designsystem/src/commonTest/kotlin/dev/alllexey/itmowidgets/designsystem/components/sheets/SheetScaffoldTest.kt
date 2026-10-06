package dev.alllexey.itmowidgets.designsystem.components.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
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
    fun closeIsALabelledTargetInBothPlacementsAndStyles() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        var closes = 0
        setContent {
            ItmoTheme(platformStyle = style) {
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

        ItmoPlatformStyle.entries.forEach {
            style = it
            SheetClosePlacement.entries.forEach { placement ->
                // The iOS text button shows the label; Material's icon and the iOS close mark carry it for TalkBack.
                onNode(hasContentDescription("$CLOSE $placement").or(hasText("$CLOSE $placement"))).performClick()
            }
            assertTouchTargets(it.minTouchTarget)
            assertNoTextOverflow()
        }

        assertEquals(4, closes)
    }

    @Test
    fun theFormModeClosesOnlyThroughItsCloseButtonInBothStyles() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        var closes = 0
        setContent {
            ItmoTheme(platformStyle = style) {
                SheetScaffold(
                    title = TITLE,
                    subtitle = SUBTITLE,
                    handle = false,
                    close = SheetClose(CLOSE, onClick = { closes++ }),
                    footer = { Text(FOOTER) },
                ) {
                    Text(BODY)
                }
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            onNodeWithText(BODY).performClick()
            onNodeWithText(FOOTER).performClick()
            onNodeWithText(TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).performClick()
            onNodeWithContentDescription(CLOSE).assertHasClickAction().performClick()
            assertTouchTargets(it.minTouchTarget)
        }

        assertEquals(2, closes)
    }

    @Test
    fun titleIsAHeading() = runComposeUiTest {
        setContent { ItmoTheme { SheetScaffold(title = TITLE, subtitle = SUBTITLE) {} } }

        onNodeWithText(TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        onNodeWithText(SUBTITLE).assertIsDisplayed()
    }

    @Test
    fun aLongListScrollsInsideTheBodyAndTheFooterStaysOnScreenInBothStyles() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        setContent {
            ItmoTheme(platformStyle = style) {
                SheetScaffold(
                    title = TITLE,
                    modifier = Modifier.testTag(SHEET).height(SHEET_HEIGHT),
                    footer = { Text(FOOTER) },
                ) {
                    LazyColumn { items(ROWS) { Box(Modifier.fillMaxWidth().height(48.dp)) } }
                }
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            val sheet = onNodeWithTag(SHEET).getBoundsInRoot()
            val footer = onNodeWithText(FOOTER).assertIsDisplayed().getBoundsInRoot()
            assertEquals(SHEET_HEIGHT, sheet.bottom - sheet.top)
            assertTrue(footer.bottom <= sheet.bottom)
        }
    }

    @Test
    fun aBoundedSheetIsAsTallAsTheMinimumBodyNeedsInBothStyles() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        setContent {
            ItmoTheme(platformStyle = style) {
                SheetScaffold(title = TITLE, modifier = Modifier.testTag(SHEET), contentMinHeight = 288.dp) {}
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            onNodeWithTag(SHEET).assertHeightIsAtLeast(288.dp)
        }
    }

    private companion object {
        const val TITLE = "Ссылки"
        const val SUBTITLE = "Математический анализ"
        const val CLOSE = "Закрыть"
        const val FOOTER = "Добавить ссылку"
        const val BODY = "Текст отзыва"
        const val SHEET = "sheet"
        const val ROWS = 40
        val SHEET_HEIGHT = 400.dp
    }
}
