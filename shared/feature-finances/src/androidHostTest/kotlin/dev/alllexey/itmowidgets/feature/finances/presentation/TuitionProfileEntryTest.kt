package dev.alllexey.itmowidgets.feature.finances.presentation

import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.profile.ProfileEntry
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.finances.FinancesHarness
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.finances.FinancesStrings
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

class TuitionProfileEntryTest {

    private val dispatcher = StandardTestDispatcher()
    private val harness = FinancesHarness(dispatcher)

    @Test
    fun aBudgetStudentHasNoRowAndNothingMoreIsAsked() = runTest(dispatcher) {
        harness.budget()

        assertNull(entry())
        assertEquals(listOf(FinancesHarness.AVAILABILITY), harness.paths())
    }

    @Test
    fun aPaidStudentHasTheRowAndItOpensTheContracts() = runTest(dispatcher) {
        harness.paid("payments-debt.json")

        assertEquals(
            ProfileEntry(
                id = "tuition",
                icon = AppIcon.PAYMENTS,
                title = UiText.Res(FinancesStrings.tuition_title),
                subtitle = UiText.Res(CoreStrings.contract_kind_tuition),
                route = AppRoutes.Tuition,
            ),
            entry(),
        )
    }

    @Test
    fun tuitionWithoutAnyContractHasNoRow() = runTest(dispatcher) {
        for (payments in listOf("payments-empty.json", "payments-null.json")) {
            harness.paid(payments)
            harness.clock.advanceBy(1.days)

            assertNull(entry(), payments)
        }
    }

    @Test
    fun aRecentAnswerIsReadFromTheDeviceAndAnOldOneIsAskedAgain() = runTest(dispatcher) {
        harness.paid("payments-debt.json")
        harness.repository().refresh()

        harness.clock.advanceBy(1.hours)
        assertEquals(AppRoutes.Tuition, entry()?.route)
        assertEquals(1, harness.paths().count { it == FinancesHarness.AVAILABILITY })

        harness.budget()
        harness.clock.advanceBy(3.hours)
        assertNull(entry())
        assertEquals(2, harness.paths().count { it == FinancesHarness.AVAILABILITY })
    }

    @Test
    fun offlineTheRowFollowsTheStoredAnswer() = runTest(dispatcher) {
        harness.paid("payments-debt.json")
        harness.repository().refresh()
        harness.offline = true
        harness.clock.advanceBy(1.days)

        assertEquals(AppRoutes.Tuition, entry()?.route)
    }

    @Test
    fun anAnswerMyItmoApiCannotReadKeepsTheStoredRow() = runTest(dispatcher) {
        harness.paid("payments-debt.json")
        harness.repository().refresh()
        harness.paid("payments-garbled.json")
        harness.clock.advanceBy(1.days)

        assertEquals(AppRoutes.Tuition, entry()?.route)
        assertEquals(2, harness.paths().count { it == FinancesHarness.PAYMENTS })
    }

    /** The row a new Профиль reads, once the refresh it may start has landed. */
    private fun TestScope.entry(): ProfileEntry? {
        val values = mutableListOf<ProfileEntry?>()
        val source = TuitionProfileEntrySource(harness.repository(), harness.clock)
        val reading = launch { source.observe().collect { values += it } }
        advanceUntilIdle()
        reading.cancel()
        return values.last()
    }
}
