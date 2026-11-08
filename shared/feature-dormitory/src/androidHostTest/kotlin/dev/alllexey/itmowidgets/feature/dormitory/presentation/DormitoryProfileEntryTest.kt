package dev.alllexey.itmowidgets.feature.dormitory.presentation

import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.profile.ProfileEntry
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.dormitory.DormitoryHarness
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class DormitoryProfileEntryTest {

    private val dispatcher = StandardTestDispatcher()
    private val harness = DormitoryHarness(dispatcher)

    @Test
    fun theRowIsHiddenWithoutAnApplication() = runTest(dispatcher) {
        harness.answer("status-empty.json")

        assertNull(entry())
    }

    @Test
    fun theRowNamesTheStageAndOpensTheDormitory() = runTest(dispatcher) {
        harness.answer("status-offered.json", documents = "documents.json")

        assertEquals(
            ProfileEntry(
                id = "dormitory",
                icon = AppIcon.APARTMENT,
                title = UiText.Res(DormitoryStrings.dormitory_title),
                subtitle = UiText.Res(DormitoryStrings.dormitory_stage_place_offered),
                route = AppRoutes.Dormitory,
            ),
            entry(),
        )
    }

    @Test
    fun aRecentAnswerIsReadFromTheDeviceAndAnOldOneIsAskedAgain() = runTest(dispatcher) {
        harness.answer("status-queue.json")
        harness.repository().refresh()

        harness.clock.advanceBy(1.hours)
        assertEquals(UiText.Res(DormitoryStrings.dormitory_stage_queue), entry()?.subtitle)
        assertEquals(1, harness.paths().size)

        harness.answer("status-settled.json")
        harness.clock.advanceBy(3.hours)
        assertEquals(UiText.Res(DormitoryStrings.dormitory_stage_settled), entry()?.subtitle)
        assertEquals(2, harness.paths().size)
    }

    @Test
    fun offlineTheRowFollowsTheStoredAnswer() = runTest(dispatcher) {
        harness.answer("status-settled.json")
        harness.repository().refresh()
        harness.offline = true
        harness.clock.advanceBy(1.days)

        assertEquals(UiText.Res(DormitoryStrings.dormitory_stage_settled), entry()?.subtitle)
    }

    /** The row a new Профиль reads, once the refresh it may start has landed. */
    private fun TestScope.entry(): ProfileEntry? {
        val values = mutableListOf<ProfileEntry?>()
        val source = DormitoryProfileEntrySource(harness.repository(), harness.clock)
        val reading = launch { source.observe().collect { values += it } }
        advanceUntilIdle()
        reading.cancel()
        return values.last()
    }
}
