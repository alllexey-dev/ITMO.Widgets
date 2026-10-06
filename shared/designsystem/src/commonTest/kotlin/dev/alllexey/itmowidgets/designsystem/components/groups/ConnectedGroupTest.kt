package dev.alllexey.itmowidgets.designsystem.components.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ConnectedGroupTest {
    @Test
    fun rowsAfterTheFirstStandTheGroupGapApart() = runComposeUiTest {
        setContent {
            // Material's 2 dp gap; under the iOS style the rows of a group touch (DS-IOS-03).
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Column {
                    GroupPosition.entries.forEach { position ->
                        Box(Modifier.fillMaxWidth().connectedGroupItem(position).height(ROW_HEIGHT).testTag(position.name))
                    }
                }
            }
        }

        val first = onNodeWithTag(GroupPosition.Single.name).getBoundsInRoot()
        val second = onNodeWithTag(GroupPosition.First.name).getBoundsInRoot()
        val third = onNodeWithTag(GroupPosition.Middle.name).getBoundsInRoot()
        val fourth = onNodeWithTag(GroupPosition.Last.name).getBoundsInRoot()

        assertEquals(0.dp, first.top)
        assertEquals(first.bottom, second.top, "a first row adds no gap above it")
        assertEquals(second.bottom + 2.dp, third.top)
        assertEquals(third.bottom + 2.dp, fourth.top)
    }

    @Test
    fun headingsAreHeadingsAndASubheadingIsReadOnce() = runComposeUiTest {
        setContent {
            ItmoTheme {
                Column {
                    SectionHeading(SECTION)
                    SectionSubheading(GROUP, value = SCORE)
                }
            }
        }

        onNodeWithText(SECTION).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        onNodeWithText(GROUP).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        onNodeWithText(SCORE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertCountEquals(2)
    }

    @Test
    fun theActionRowIsOneTargetWithSilentIcons() = runComposeUiTest {
        var clicks = 0
        setContent {
            ItmoTheme {
                GroupActionRow(
                    ALL_LINKS,
                    ColorPainter(Color.Black),
                    onClick = { clicks++ },
                    modifier = Modifier.connectedGroupItem(GroupPosition.Last),
                )
            }
        }

        onNodeWithText(ALL_LINKS).assertHeightIsAtLeast(56.dp).performClick()

        assertEquals(1, clicks)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertCountEquals(0)
        assertTouchTargets()
    }

    private companion object {
        const val SECTION = "Ссылки"
        const val GROUP = "Лабораторные"
        const val SCORE = "30 / 48"
        const val ALL_LINKS = "Все ссылки, 5"
        val ROW_HEIGHT = 56.dp
    }
}
