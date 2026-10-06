package dev.alllexey.itmowidgets.feature.schedule.ui.list

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDaySummary
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.SelectedUser
import dev.alllexey.itmowidgets.feature.schedule.ui.list.preview.ScheduleListPreviewData
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ScheduleScreenTest {

    private val ownDays = ScheduleListPreviewData.days(ScheduleListPreviewData.ownDays)

    @Test
    fun everyBodyShowsItsOwnAreaAndEveryTargetIsAtLeast48Dp() = runComposeUiTest {
        var body by mutableStateOf<ScheduleScreenBody>(ScheduleScreenBody.Loading)
        setContent { ItmoTheme { ScheduleScreen(state(body), ScheduleScreenActions()) } }

        val shown = listOf(
            ScheduleScreenBody.Loading to ScheduleScreenTestTags.LOADING,
            ScheduleScreenBody.Days(ownDays) to ScheduleScreenTestTags.LIST,
            ScheduleScreenBody.Empty to ScheduleScreenTestTags.STATE,
            ScheduleScreenBody.Failed(AppError.Network) to ScheduleScreenTestTags.STATE,
        )
        val areas = shown.map { it.second }.distinct()
        for ((value, tag) in shown) {
            body = value
            waitForIdle()
            assertTouchTargets()
            for (area in areas) {
                if (area == tag) onNodeWithTag(area).assertExists() else onNodeWithTag(area).assertDoesNotExist()
            }
        }
    }

    @Test
    fun rowsOpenTheirLessonOnItsDayAndTheBookingAndTheErrorRetries() = runComposeUiTest {
        val lessons = mutableListOf<Pair<Long, LocalDate>>()
        val bookings = mutableListOf<PendingSportBooking>()
        var retries = 0
        var body by mutableStateOf<ScheduleScreenBody>(ScheduleScreenBody.Days(ownDays))
        val actions = ScheduleScreenActions(
            onRetry = { retries++ },
            onLessonClick = { lesson, date -> lessons += lesson.pairId to date },
            onPendingClick = { bookings += it },
        )
        setContent {
            ItmoTheme {
                ScheduleScreen(state(body), actions, listState = rememberTodayState())
            }
        }

        for (row in listOf(ScheduleListTestTags.lesson(3), ScheduleListTestTags.pending(1))) {
            onNodeWithTag(ScheduleScreenTestTags.LIST).performScrollToNode(hasTestTag(row))
            onNodeWithTag(row).performClick()
        }
        body = ScheduleScreenBody.Failed(AppError.Network)
        waitForIdle()
        onNodeWithText("Повторить").performClick()

        assertEquals(listOf(3L to ScheduleListPreviewData.today), lessons)
        assertEquals(listOf(1L), bookings.map { it.queueId })
        assertEquals(1, retries)
    }

    @Test
    fun aSelectedFriendReplacesTheFriendsButtonWithItsCard() = runComposeUiTest {
        var picks = 0
        var clears = 0
        var friend by mutableStateOf(false)
        val actions = ScheduleScreenActions(onPickFriend = { picks++ }, onClearFriend = { clears++ })
        setContent {
            ItmoTheme {
                val selected = if (friend) ScheduleListPreviewData.friend else null
                ScheduleScreen(state(ScheduleScreenBody.Days(ownDays), selected), actions)
            }
        }

        onNodeWithTag(ScheduleScreenTestTags.SELECTED_USER).assertDoesNotExist()
        onNodeWithTag(ScheduleScreenTestTags.FRIENDS_BUTTON).performClick()
        friend = true
        waitForIdle()
        onNodeWithTag(ScheduleScreenTestTags.FRIENDS_BUTTON).assertDoesNotExist()
        onNodeWithText("Сменить").performClick()
        onNodeWithContentDescription("Вернуться к своему").performClick()

        assertEquals(2, picks)
        assertEquals(1, clears)
    }

    @Test
    fun theGutterKeepsLargeTimesClearOfTheMarker() = runComposeUiTest {
        val day = ScheduleListPreviewData.dayOf(ScheduleListPreviewData.today)
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ItmoTheme { ScheduleDayCard(day, onLessonClick = {}, onPendingClick = {}) }
            }
        }

        val time = onNodeWithText("08:20").getUnclippedBoundsInRoot()
        val marker = onAllNodesWithContentDescription("Пара завершилась")[0].getUnclippedBoundsInRoot()
        assertTrue(time.right > time.left, "08:20 is laid out")
        assertTrue(time.right < marker.left, "08:20 ends at ${time.right}, the marker starts at ${marker.left}")
        assertTrue(time.left.value >= 0f, "08:20 starts at ${time.left}")
    }

    @Test
    fun theStateFollowsTheViewModelAtOneInstant() {
        val day = DaySchedule(1, 1, ScheduleListPreviewData.today, null, listOf(lesson(LocalTime(11, 40))))
        fun of(state: ScheduleUiState, ownTab: Boolean = true) = scheduleScreenState(
            state,
            ScheduleListPreviewData.now,
            ScheduleListPreviewData.timeZone,
            ownTab,
        )

        val content = of(ScheduleUiState.Content(listOf(day), loadingMore = true, selectedUser = null))
        val days = assertIs<ScheduleScreenBody.Days>(content.body).days
        assertEquals(ScheduleDaySummary.Lessons(1), days.single().summary)
        assertTrue(content.refreshing)
        assertTrue(content.canPickFriend)

        val friend = of(ScheduleUiState.Loading(ScheduleListPreviewData.friend))
        assertEquals(ScheduleScreenBody.Loading, friend.body)
        assertFalse(friend.canPickFriend)
        assertFalse(friend.refreshing)
        assertFalse(of(ScheduleUiState.Empty(null), ownTab = false).canPickFriend)
        assertEquals(
            ScheduleScreenBody.Failed(AppError.Network),
            of(ScheduleUiState.Error(AppError.Network, null)).body,
        )
    }

    private fun state(body: ScheduleScreenBody, selected: SelectedUser? = null) = ScheduleScreenState(
        body,
        selected,
        canPickFriend = selected == null,
        refreshing = false,
    )

    private fun lesson(start: LocalTime): Lesson = ScheduleListPreviewData.lesson(1, start, "Физика", 3)
}

/** The host opens the own schedule on today, the second preview day. */
@Composable
private fun rememberTodayState() = rememberLazyListState(initialFirstVisibleItemIndex = 1)
