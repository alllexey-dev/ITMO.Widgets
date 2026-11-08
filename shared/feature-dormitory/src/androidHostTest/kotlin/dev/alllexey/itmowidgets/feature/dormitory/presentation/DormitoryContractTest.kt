package dev.alllexey.itmowidgets.feature.dormitory.presentation

import dev.alllexey.itmowidgets.core.text.BalanceLine
import dev.alllexey.itmowidgets.core.text.PaymentPill
import dev.alllexey.itmowidgets.core.text.PaymentTone
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.dormitory.DormitoryHarness
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * The Договор найма on `Общежитие`: balance, the schedule of Платежи and what is paid, a Долг, earlier academic years
 * under `Прошлые годы`. Today is 8 November 2026 in Moscow.
 */
class DormitoryContractTest {

    private val main = TestMainDispatcher()
    private lateinit var harness: DormitoryHarness

    @BeforeTest
    fun setUp() {
        main.install()
        harness = DormitoryHarness(main.dispatcher)
    }

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun aResidentSeesTheBalanceAndEveryPaymentWithWhatIsPaid() = runTest(main.dispatcher) {
        harness.answer("status-settled.json")
        harness.contracts("periods-current.json", mapOf("2026-01-01" to "contracts-debt.json"))

        val contracts = shown().contracts

        assertEquals(
            ContractCard(
                id = "00-00000001",
                title = UiText.Res(CoreStrings.contract_kind_rent),
                subtitle = UiText.Res(CoreStrings.contract_subtitle, listOf("0000.00.00.0001", "1 сентября 2026", "30 июня 2027")),
                balance = BalanceLine(UiText.Res(CoreStrings.contract_debt), rubles("1${NBSP}500"), PaymentTone.DEBT),
                pay = true,
                payments = listOf(
                    PaymentRow(
                        "Сентябрь 2026",
                        rubles("1${NBSP}500,50"),
                        due("10 сентября"),
                        paid = null,
                        pill = PaymentPill(UiText.Res(CoreStrings.payment_paid), PaymentTone.DONE),
                    ),
                    PaymentRow(
                        "Октябрь 2026",
                        rubles("1${NBSP}500"),
                        due("10 октября"),
                        paid = null,
                        pill = PaymentPill(UiText.Res(CoreStrings.payment_overdue), PaymentTone.DEBT),
                    ),
                    PaymentRow(
                        "Ноябрь 2026",
                        rubles("1${NBSP}500"),
                        due("8 ноября"),
                        paid = null,
                        pill = PaymentPill(UiText.Res(CoreStrings.payment_due_today), PaymentTone.ATTENTION),
                    ),
                    PaymentRow("Декабрь 2026", rubles("12${NBSP}000"), due("10 декабря"), paid = null, pill = null),
                ),
            ),
            contracts?.current?.single(),
        )
        assertNull(contracts?.pastYears)
        assertEquals(
            listOf(DormitoryHarness.STATUS, DormitoryHarness.PERIODS, DormitoryHarness.CONTRACTS),
            harness.paths(),
        )
        val query = harness.requests.last().url.parameters
        assertEquals("2026-01-01" to "2026-12-31", query["from"] to query["to"])
    }

    @Test
    fun anOverpaymentAndAPartlyPaidPaymentDueWithinAWeek() = runTest(main.dispatcher) {
        harness.answer("status-settled.json")
        harness.contracts("periods-current.json", mapOf("2026-01-01" to "contracts-clear.json"))

        val card = checkNotNull(shown().contracts).current.single()

        assertEquals(BalanceLine(UiText.Res(CoreStrings.contract_overpaid), rubles("250,75"), PaymentTone.DONE), card.balance)
        assertEquals(
            PaymentRow(
                "Ноябрь 2026",
                rubles("1${NBSP}500"),
                due("13 ноября"),
                paid = UiText.Res(CoreStrings.payment_paid_part, listOf(rubles("500"))),
                pill = PaymentPill(UiText.Plural(CorePlurals.payment_due_in_days, 5, listOf(5)), PaymentTone.ATTENTION),
            ),
            card.payments.last(),
        )
        assertTrue(card.pay)
    }

    @Test
    fun aContractInTwoCalendarYearsIsShownOnceAsTheLatestPeriodHasIt() = runTest(main.dispatcher) {
        harness.answer("status-settled.json")
        harness.contracts(
            "periods-two-years.json",
            mapOf("2025-01-01" to "contracts-overlap-earlier.json", "2026-01-01" to "contracts-overlap-latest.json"),
        )

        val contracts = checkNotNull(shown().contracts)

        assertEquals(listOf("00-00000002", "00-00000005"), contracts.current.map { it.id })
        val card = contracts.current.last()
        assertEquals(BalanceLine(UiText.Res(CoreStrings.contract_debt), rubles("1${NBSP}400"), PaymentTone.DEBT), card.balance)
        assertEquals(PaymentPill(UiText.Res(CoreStrings.payment_paid), PaymentTone.DONE), card.payments.first().pill)
        assertNull(contracts.pastYears)
    }

    /** The 2025/2026 Договор comes in the 2026 calendar period too and still folds under its academic year. */
    @Test
    fun earlierAcademicYearsFoldIntoPastYears() = runTest(main.dispatcher) {
        harness.answer("status-settled.json")
        harness.contracts(
            "periods-two-years.json",
            mapOf("2025-01-01" to "contracts-past.json", "2026-01-01" to "contracts-clear-and-past.json"),
        )

        val viewModel = viewModel().also { advanceUntilIdle() }
        val contracts = checkNotNull((viewModel.uiState.value.content as DormitoryContent.Shown).contracts)

        assertEquals(listOf("00-00000002"), contracts.current.map { it.id })
        val past = checkNotNull(contracts.pastYears)
        assertEquals(false, past.expanded)
        assertEquals(listOf(UiText.Res(CoreStrings.contract_academic_year, listOf("2025", "2026"))), past.years.map { it.title })
        val card = past.years.single().contracts.single()
        assertEquals(BalanceLine(UiText.Res(CoreStrings.contract_balance), rubles("0"), PaymentTone.NEUTRAL), card.balance)
        assertEquals(false, card.pay)

        viewModel.togglePastYears()
        advanceUntilIdle()
        assertEquals(true, (viewModel.uiState.value.content as DormitoryContent.Shown).contracts?.pastYears?.expanded)
    }

    @Test
    fun aDebtFromAnEarlierYearStaysInFront() = runTest(main.dispatcher) {
        harness.answer("status-settled.json")
        harness.contracts(
            "periods-two-years.json",
            mapOf("2025-01-01" to "contracts-past-debt.json", "2026-01-01" to "contracts-clear.json"),
        )

        val contracts = checkNotNull(shown().contracts)

        assertEquals(listOf("00-00000002", "00-00000004"), contracts.current.map { it.id })
        assertEquals(
            BalanceLine(UiText.Res(CoreStrings.contract_debt), rubles("700"), PaymentTone.DEBT),
            contracts.current.last().balance,
        )
        assertNull(contracts.pastYears)
    }

    @Test
    fun aContractWithoutAnApplicationIsShownWithoutAStage() = runTest(main.dispatcher) {
        harness.answer("status-empty.json")
        harness.contracts("periods-current.json", mapOf("2026-01-01" to "contracts-clear.json"))

        val shown = shown()

        assertNull(shown.stage)
        assertNull(shown.dormitory)
        assertEquals(listOf("00-00000002"), shown.contracts?.current?.map { it.id })
    }

    @Test
    fun offlineTheContractsComeFromTheStoredAnswer() = runTest(main.dispatcher) {
        harness.answer("status-settled.json")
        harness.contracts("periods-current.json", mapOf("2026-01-01" to "contracts-debt.json"))
        viewModel().also { advanceUntilIdle() }

        harness.offline = true
        harness.clock.advanceBy(2.hours)
        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        val card = checkNotNull((state.content as DormitoryContent.Shown).contracts).current.single()
        assertEquals(rubles("1${NBSP}500"), card.balance.amount)
        assertEquals(4, card.payments.size)
        assertEquals(UiText.Res(DormitoryStrings.dormitory_updated_hours, listOf(2)), state.stale)
    }

    @Test
    fun withoutContractsThereIsNoContractSection() = runTest(main.dispatcher) {
        harness.answer("status-settled.json")

        assertNull(shown().contracts)
    }

    private fun TestScope.shown(): DormitoryContent.Shown =
        viewModel().also { advanceUntilIdle() }.uiState.value.content as DormitoryContent.Shown

    private fun viewModel() = DormitoryViewModel(harness.repository(), harness.time)

    private fun rubles(digits: String) = UiText.Dynamic("$digits$NBSP\u20BD")

    private fun due(day: String) = UiText.Res(CoreStrings.payment_due, listOf(day))

    private companion object {
        const val NBSP = "\u00A0"
    }
}
