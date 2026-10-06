package dev.alllexey.itmowidgets.feature.schedule.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardTestTags
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.ui.home.preview.ScheduleHomePreviewSamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ScheduleHomeCardsTest {

    private val renderer = ScheduleHomeCardRenderer(ScheduleHomePreviewSamples.zone)

    @Test
    fun theRendererClaimsTheScheduleAndChangesCards() {
        assertEquals(setOf(HomeCardKind.SCHEDULE, HomeCardKind.SCHEDULE_CHANGES), renderer.kinds)
    }

    @Test
    fun rowsOpenTheirLessonAndTheChangesCardOpensAndIsMarkedRead() = runComposeUiTest {
        val calls = mutableListOf<String>()
        var lesson: LessonDetailsArgs? = null
        var pending: PendingSportDetailsArgs? = null
        val actions = HomeCardActions(
            onLesson = { lesson = it },
            onPendingSport = { pending = it },
            onOpenScheduleChanges = { calls += "changes" },
            onDismiss = { calls += "read-$it" },
        )
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Column {
                    ScheduleHomePreviewSamples.cards().forEach { card ->
                        renderer.Content(card, actions, Modifier.testTag(card.kind.name))
                    }
                }
            }
        }
        assertTouchTargets()

        onAllNodesWithTag(HomeCardTestTags.SCHEDULE_ROW)[0].performClick()
        assertEquals("Математический анализ", lesson?.subjectName)
        onAllNodesWithTag(HomeCardTestTags.SCHEDULE_ROW)[2].performClick()
        assertEquals("Плавание", pending?.sectionName)

        val changes = onNodeWithTag(HomeCardKind.SCHEDULE_CHANGES.name)
        changes.assertContentDescriptionEquals(
            "Изменения в расписании, 3. Математический анализ \u2014 перенесена на ср, 9 сентября, 10:00",
        )
        changes.performClick()
        onNodeWithTag(HomeCardTestTags.DISMISS).performClick()

        assertEquals(listOf("changes", "read-${HomeCardKind.SCHEDULE_CHANGES}"), calls)
    }
}
