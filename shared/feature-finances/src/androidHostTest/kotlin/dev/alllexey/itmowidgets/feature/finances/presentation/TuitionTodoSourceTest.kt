package dev.alllexey.itmowidgets.feature.finances.presentation

import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.today.TodayTodo
import dev.alllexey.itmowidgets.feature.finances.FinancesHarness
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** The tuition part of `Нужно сделать`, from the stored answer. Today is 8 November 2026 in Moscow. */
class TuitionTodoSourceTest {

    private val dispatcher = StandardTestDispatcher()
    private val harness = FinancesHarness(dispatcher)

    @Test
    fun aDebtShowsWithWhatIsOwedUntilItIsPaidOff() = runTest(dispatcher) {
        harness.paid("payments-debt.json")
        harness.repository().refresh()

        assertEquals(
            listOf(
                TodayTodo(
                    id = "tuition-debt-00-00000010",
                    icon = AppIcon.ERROR,
                    title = UiText.Res(CoreStrings.payment_todo_debt_title, listOf(rubles("60${NBSP}000,50"))),
                    reason = UiText.Res(
                        CoreStrings.payment_todo_debt_reason,
                        listOf(UiText.Res(CoreStrings.contract_kind_tuition), "T-0000-0010"),
                    ),
                    action = UiText.Res(CoreStrings.payment_todo_open),
                    target = EntryRoute(AppTab.HOME, overlay = AppRoutes.Tuition),
                ),
            ),
            todos(),
        )

        harness.paid("payments-debt-paid.json")
        harness.repository().refresh()
        assertEquals(emptyList(), todos())
    }

    @Test
    fun aPaymentDueWithinAWeekShowsWhatIsLeftOfIt() = runTest(dispatcher) {
        harness.paid("payments-two-years.json")
        harness.repository().refresh()

        assertEquals(
            listOf(
                TodayTodo(
                    id = "tuition-payment-00-00000012-2026-11-13",
                    icon = AppIcon.PAYMENTS,
                    title = UiText.Res(CoreStrings.payment_todo_pay_title, listOf("13 ноября", rubles("100${NBSP}000"))),
                    reason = UiText.Joined(
                        listOf(
                            UiText.Res(CoreStrings.contract_kind_tuition),
                            UiText.LowercaseFirst(UiText.Plural(CorePlurals.payment_due_in_days, 5, listOf(5))),
                        ),
                        " · ",
                    ),
                    action = UiText.Res(CoreStrings.payment_todo_open),
                    target = EntryRoute(AppTab.HOME, overlay = AppRoutes.Tuition),
                ),
            ),
            todos(),
        )
    }

    /** On 13 November the Платёж is due today; a day later it is overdue and the Договор's Долг takes its place. */
    @Test
    fun offlineAnUnpaidPaymentIsDueTodayThenADebt() = runTest(dispatcher) {
        harness.paid("payments-two-years.json")
        harness.repository().refresh()
        val sent = harness.requests.size
        harness.offline = true

        harness.clock.advanceBy(5.days)
        assertEquals(
            UiText.LowercaseFirst(UiText.Res(CoreStrings.payment_due_today)),
            (todos().single().reason as UiText.Joined).parts.last(),
        )

        harness.clock.advanceBy(1.days)
        val debt = todos().single()
        assertEquals("tuition-debt-00-00000012", debt.id)
        assertEquals(UiText.Res(CoreStrings.payment_todo_debt_title, listOf(rubles("100${NBSP}000"))), debt.title)
        assertEquals(sent, harness.requests.size)
    }

    @Test
    fun aBudgetPlaceAndSignOutNeedNothing() = runTest(dispatcher) {
        harness.budget()
        harness.repository().refresh()
        assertEquals(emptyList(), todos())

        harness.paid("payments-debt.json")
        val repository = harness.repository()
        repository.refresh()
        repository.clearSessionData()
        assertEquals(emptyList(), todos())
    }

    private fun rubles(digits: String) = UiText.Res(CoreStrings.payment_money, listOf(digits))

    /** The items a new Сегодня reads from the device, after a restart of the app. */
    private fun TestScope.todos(): List<TodayTodo> {
        val values = mutableListOf<List<TodayTodo>>()
        val source = TuitionTodoSource(harness.repository(), harness.time)
        val reading = launch { source.observe().collect { values += it } }
        advanceUntilIdle()
        reading.cancel()
        return values.lastOrNull() ?: emptyList()
    }

    private companion object {
        const val NBSP = "\u00A0"
    }
}
