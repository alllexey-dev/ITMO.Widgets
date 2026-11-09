package dev.alllexey.itmowidgets.feature.dormitory.presentation

import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.today.TodayTodo
import dev.alllexey.itmowidgets.feature.dormitory.DormitoryHarness
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class DormitoryTodoSourceTest {

    private val dispatcher = StandardTestDispatcher()
    private val harness = DormitoryHarness(dispatcher)

    @Test
    fun anOfferedPlaceAsksToChooseADateAndToBringEachMissingDocument() = runTest(dispatcher) {
        harness.answer("status-offered.json", documents = "documents.json")
        harness.repository().refresh()

        assertEquals(
            listOf(
                TodayTodo(
                    id = "dormitory-date",
                    icon = AppIcon.CALENDAR_MONTH,
                    title = UiText.Res(DormitoryStrings.dormitory_todo_date_title),
                    reason = UiText.Res(DormitoryStrings.dormitory_todo_date_reason, listOf("12:00")),
                    action = UiText.Res(DormitoryStrings.dormitory_todo_open),
                    target = EntryRoute(AppTab.HOME, overlay = AppRoutes.Dormitory),
                ),
                TodayTodo(
                    id = "dormitory-document-Синтетическая справка",
                    icon = AppIcon.ASSIGNMENT,
                    title = UiText.Res(DormitoryStrings.dormitory_todo_document_title, listOf("Синтетическая справка")),
                    reason = UiText.Res(DormitoryStrings.dormitory_todo_document_reason, listOf("Отсутствует")),
                    action = UiText.Res(DormitoryStrings.dormitory_todo_open),
                    target = EntryRoute(AppTab.HOME, overlay = AppRoutes.Dormitory),
                ),
            ),
            todos(),
        )
    }

    @Test
    fun aChosenDateLeavesTheMissingDocumentsUntilItmoConfirmsThem() = runTest(dispatcher) {
        harness.answer("status-date-chosen.json", documents = "documents.json")
        harness.repository().refresh()
        assertEquals(listOf("dormitory-document-Синтетическая справка"), todos().map { it.id })

        harness.answer("status-date-chosen.json", documents = "documents-confirmed.json")
        harness.repository().refresh()
        assertEquals(emptyList(), todos())
    }

    @Test
    fun theQueueASettledStudentAndNoApplicationNeedNothing() = runTest(dispatcher) {
        for (status in listOf("status-queue.json", "status-settled.json", "status-empty.json")) {
            harness.answer(status)
            harness.repository().refresh()

            assertEquals(emptyList(), todos(), status)
        }
    }

    @Test
    fun theTimeThePlaceWasFirstSeenSurvivesRestartsAndLaterAnswers() = runTest(dispatcher) {
        harness.answer("status-offered.json", documents = "documents.json")
        harness.repository().refresh()

        harness.clock.advanceBy(5.hours)
        harness.repository().refresh()
        assertEquals(reasonSince("12:00"), todos().first().reason)

        harness.clock.advanceBy(1.days)
        harness.repository().refresh()
        assertEquals(reasonSince("8 нояб., 12:00"), todos().first().reason)
    }

    @Test
    fun aStageThatLeavesAndComesBackIsSeenAnew() = runTest(dispatcher) {
        harness.answer("status-offered.json", documents = "documents.json")
        harness.repository().refresh()

        harness.clock.advanceBy(2.hours)
        harness.answer("status-date-chosen.json")
        harness.repository().refresh()
        harness.clock.advanceBy(2.hours)
        harness.answer("status-offered.json")
        harness.repository().refresh()

        assertEquals(reasonSince("16:00"), todos().first().reason)
    }

    @Test
    fun signOutForgetsWhenThePlaceWasSeen() = runTest(dispatcher) {
        harness.answer("status-offered.json", documents = "documents.json")
        val repository = harness.repository()
        repository.refresh()

        repository.clearSessionData()
        assertEquals(emptyList(), todos())

        harness.clock.advanceBy(1.hours)
        harness.repository().refresh()
        assertEquals(reasonSince("13:00"), todos().first().reason)
    }

    @Test
    fun offlineTheItemsComeFromTheStoredAnswerWithoutARequest() = runTest(dispatcher) {
        harness.answer("status-offered.json", documents = "documents.json")
        harness.repository().refresh()
        val sent = harness.requests.size

        harness.offline = true
        harness.clock.advanceBy(1.days)

        assertEquals(listOf("dormitory-date", "dormitory-document-Синтетическая справка"), todos().map { it.id })
        assertEquals(sent, harness.requests.size)
    }

    @Test
    fun aPaymentShowsFromSevenDaysBeforeItsDateWithWhatIsLeftOffline() = runTest(dispatcher) {
        harness.answer("status-settled.json")
        harness.contracts("periods-current.json", mapOf("2026-01-01" to "contracts-due-in-8-days.json"))
        harness.repository().refresh()
        assertEquals(emptyList(), todos())
        val sent = harness.requests.size

        harness.offline = true
        harness.clock.advanceBy(1.days)

        assertEquals(
            listOf(
                TodayTodo(
                    id = "dormitory-payment-00-00000003-2026-11-16",
                    icon = AppIcon.PAYMENTS,
                    title = UiText.Res(CoreStrings.payment_todo_pay_title, listOf("16 ноября", rubles("1${NBSP}000"))),
                    reason = UiText.Joined(
                        listOf(
                            UiText.Res(CoreStrings.contract_kind_rent),
                            UiText.LowercaseFirst(UiText.Plural(CorePlurals.payment_due_in_days, 7, listOf(7))),
                        ),
                        " · ",
                    ),
                    action = UiText.Res(CoreStrings.payment_todo_open),
                    target = EntryRoute(AppTab.HOME, overlay = AppRoutes.Dormitory),
                ),
            ),
            todos(),
        )
        assertEquals(sent, harness.requests.size)
    }

    /** October is overdue and the balance owes it: one Долг item, not a Платёж item as well; November is due today. */
    @Test
    fun aDebtAndAPaymentDueTodayShowOnceEach() = runTest(dispatcher) {
        harness.answer("status-settled.json")
        harness.contracts("periods-current.json", mapOf("2026-01-01" to "contracts-debt.json"))
        harness.repository().refresh()

        assertEquals(
            listOf(
                TodayTodo(
                    id = "dormitory-debt-00-00000001",
                    icon = AppIcon.ERROR,
                    title = UiText.Res(CoreStrings.payment_todo_debt_title, listOf(rubles("1${NBSP}500"))),
                    reason = UiText.Res(
                        CoreStrings.payment_todo_debt_reason,
                        listOf(UiText.Res(CoreStrings.contract_kind_rent), "0000.00.00.0001"),
                    ),
                    action = UiText.Res(CoreStrings.payment_todo_open),
                    target = EntryRoute(AppTab.HOME, overlay = AppRoutes.Dormitory),
                ),
                TodayTodo(
                    id = "dormitory-payment-00-00000001-2026-11-08",
                    icon = AppIcon.PAYMENTS,
                    title = UiText.Res(CoreStrings.payment_todo_pay_title, listOf("8 ноября", rubles("1${NBSP}500"))),
                    reason = UiText.Joined(
                        listOf(
                            UiText.Res(CoreStrings.contract_kind_rent),
                            UiText.LowercaseFirst(UiText.Res(CoreStrings.payment_due_today)),
                        ),
                        " · ",
                    ),
                    action = UiText.Res(CoreStrings.payment_todo_open),
                    target = EntryRoute(AppTab.HOME, overlay = AppRoutes.Dormitory),
                ),
            ),
            todos(),
        )
    }

    @Test
    fun aDebtStaysUntilItIsPaidOff() = runTest(dispatcher) {
        harness.answer("status-settled.json")
        harness.contracts("periods-current.json", mapOf("2026-01-01" to "contracts-debt.json"))
        harness.repository().refresh()

        harness.clock.advanceBy(3.days)
        assertEquals(listOf("dormitory-debt-00-00000001"), todos().map { it.id })

        harness.contracts("periods-current.json", mapOf("2026-01-01" to "contracts-debt-paid.json"))
        harness.repository().refresh()
        assertEquals(emptyList(), todos())
    }

    /** An earlier year's Договор keeps its Долг in front; a Договор without an application still counts. */
    @Test
    fun aDebtFromAnEarlierYearAndAPaymentWithoutAnApplication() = runTest(dispatcher) {
        harness.answer("status-empty.json")
        harness.contracts(
            "periods-two-years.json",
            mapOf("2025-01-01" to "contracts-past-debt.json", "2026-01-01" to "contracts-clear.json"),
        )
        harness.repository().refresh()

        val todos = todos()

        assertEquals(listOf("dormitory-debt-00-00000004", "dormitory-payment-00-00000002-2026-11-13"), todos.map { it.id })
        assertEquals(UiText.Res(CoreStrings.payment_todo_debt_title, listOf(rubles("700"))), todos.first().title)
        assertEquals(UiText.Res(CoreStrings.payment_todo_pay_title, listOf("13 ноября", rubles("1${NBSP}000"))), todos.last().title)
    }

    private fun rubles(digits: String) = UiText.Dynamic("$digits$NBSP\u20BD")

    private fun reasonSince(since: String) = UiText.Res(DormitoryStrings.dormitory_todo_date_reason, listOf(since))

    /** The items a new Сегодня reads from the device, after a restart of the app. */
    private fun TestScope.todos(): List<TodayTodo> {
        val values = mutableListOf<List<TodayTodo>>()
        val source = DormitoryTodoSource(harness.repository(), harness.time)
        val reading = launch { source.observe().collect { values += it } }
        advanceUntilIdle()
        reading.cancel()
        return values.lastOrNull() ?: emptyList()
    }

    private companion object {
        const val NBSP = "\u00A0"
    }
}
