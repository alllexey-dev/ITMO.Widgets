package dev.alllexey.itmowidgets.feature.social.ui.reviews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.MinTouchTarget
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import dev.alllexey.itmowidgets.feature.social.ui.reviews.ReviewPreviewFixtures as F

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class TeacherSummaryCardTest {

    @Test
    fun theScalesFoldBehindA48DpToggleThatOnlyGrowsTheCard() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        var toggles = 0
        setContent { Card(F.summary, expanded) { toggles++ } }

        val toggle = onNodeWithTag(TeacherReviewTestTags.SUMMARY_TOGGLE)
            .assertHeightIsAtLeast(MinTouchTarget)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "свёрнуто"))
        onNodeWithTag(TeacherReviewTestTags.SUMMARY_SCALES).assertDoesNotExist()
        val header = onNodeWithContentDescription(HEADER).getBoundsInRoot()
        toggle.performClick()
        assertEquals(1, toggles)
        assertTouchTargets()

        expanded = true
        waitForIdle()
        onNodeWithTag(TeacherReviewTestTags.SUMMARY_TOGGLE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "развёрнуто"))
        onNodeWithTag(TeacherReviewTestTags.SUMMARY_SCALES).assertExists()
        onNodeWithContentDescription("Справедливость оценок: высокая. Оценки считают честными").assertExists()
        onNodeWithContentDescription("Нагрузка: мало данных").assertExists()
        assertEquals(header, onNodeWithContentDescription(HEADER).getBoundsInRoot())
    }

    @Test
    fun talkBackReadsTheHeaderToneAndEachBlockAsOne() = runComposeUiTest {
        setContent { Card(F.summary, expanded = false) }

        onNodeWithContentDescription(HEADER).assertExists()
        onNodeWithContentDescription("Тон отзывов: скорее положительные").assertExists()
        onNodeWithContentDescription("Плюсы: Понятно объясняет сложные темы; Честно оценивает").assertExists()
        onNodeWithContentDescription("Минусы: Строгая защита лабораторных").assertExists()
    }

    @Test
    fun lowConfidenceHidesTheToneAndEmptyBlocksAreHidden() = runComposeUiTest {
        setContent { Card(F.sparseSummary, expanded = true) }

        onNodeWithContentDescription("Сводка по 3 отзывам, составлена ИИ").assertExists()
        onNodeWithContentDescription("Тон отзывов", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("Плюсы", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("Минусы: Строгая защита лабораторных").assertExists()
        onNodeWithContentDescription("Объясняет: мало данных").assertExists()
        onNodeWithContentDescription("Нагрузка: низкая. Домашних заданий немного").assertExists()
    }

    @Composable
    private fun Card(summary: TeacherSummary, expanded: Boolean, onToggle: () -> Unit = {}) {
        ItmoTheme(platformStyle = ItmoPlatformStyle.Material) { TeacherSummaryCard(summary, expanded, onToggle) }
    }

    private companion object {
        const val HEADER = "Сводка по 12 отзывам, составлена ИИ"
    }
}
