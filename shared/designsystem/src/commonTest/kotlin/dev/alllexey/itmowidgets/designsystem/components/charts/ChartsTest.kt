package dev.alllexey.itmowidgets.designsystem.components.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ChartsTest {
    @Test
    fun scoreRingShowsItsContentAndStaysSilent() = runComposeUiTest {
        setContent {
            ItmoTheme {
                ScoreRing(
                    listOf(ScoreRingSector(Color.Blue, 60f), ScoreRingSector(Color.Red, 50f)),
                    Modifier.testTag(TAG).size(112.dp),
                    animated = true,
                ) { Text(TOTAL) }
            }
        }

        onNodeWithText(TOTAL).assertIsDisplayed()
        onNodeWithTag(TAG).assertWidthIsEqualTo(112.dp).assertHeightIsEqualTo(112.dp)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertCountEquals(0)
    }

    @Test
    fun gradeScaleMakesRoomForLabelsOnlyWhenItHasThem() = runComposeUiTest {
        setContent {
            ItmoTheme {
                Column {
                    GradeScale(80.0, listOf(GradeScaleTick(60.0, "")), Color.Green, Modifier.testTag("bare"))
                    GradeScale(null, listOf(GradeScaleTick(60.0, "E")), Color.Green, Modifier.testTag(TAG))
                }
            }
        }

        // 3 dp of tick overhang above and below an 8 dp track.
        onNodeWithTag("bare").assertHeightIsEqualTo(14.dp)
        val labelled = onNodeWithTag(TAG).getUnclippedBoundsInRoot()
        val height = labelled.bottom - labelled.top
        assertTrue(height > 14.dp + 4.dp, "$height")
    }

    @Test
    fun timelineMarkerTakesAStableAreaAndSaysItsStateOnlyWhenGiven() = runComposeUiTest {
        setContent {
            ItmoTheme {
                Column {
                    TimelineMarkerState.entries.forEach { TimelineMarker(it, it.name, Modifier.testTag(it.name)) }
                    TimelineMarker(TimelineMarkerState.Upcoming, contentDescription = null, Modifier.testTag(TAG))
                }
            }
        }

        TimelineMarkerState.entries.forEach {
            onNodeWithTag(it.name).assertWidthIsEqualTo(14.dp).assertHeightIsEqualTo(14.dp)
                .assertContentDescriptionEquals(it.name)
        }
        onNodeWithTag(TAG).assertWidthIsEqualTo(14.dp)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assertCountEquals(TimelineMarkerState.entries.size)
    }

    @Test
    fun stepsIndicatorIsOneNodeWithTheCurrentStepStretched() = runComposeUiTest {
        setContent {
            ItmoTheme { StepsIndicator(count = 4, current = 1, contentDescription = STEP, Modifier.testTag(TAG)) }
        }

        // Three 8 dp dots, one 24 dp pill and three 8 dp gaps.
        onNodeWithTag(TAG).assertContentDescriptionEquals(STEP).assertWidthIsEqualTo(72.dp).assertHeightIsEqualTo(8.dp)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertCountEquals(1)
    }

    private companion object {
        const val TAG = "chart"
        const val TOTAL = "110"
        const val STEP = "Шаг 2 из 4"
    }
}
