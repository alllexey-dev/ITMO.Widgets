package dev.alllexey.itmowidgets.feature.finances.presentation

import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.BalanceLine
import dev.alllexey.itmowidgets.core.text.ContractCard
import dev.alllexey.itmowidgets.core.text.PaymentPill
import dev.alllexey.itmowidgets.core.text.PaymentRow
import dev.alllexey.itmowidgets.core.text.PaymentTone
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.finances.FinancesHarness
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

/** `Оплата обучения` over my.itmo. Today is 8 November 2026 in Moscow. */
class TuitionViewModelTest {

    private val main = TestMainDispatcher()
    private lateinit var harness: FinancesHarness

    @BeforeTest
    fun setUp() {
        main.install()
        harness = FinancesHarness(main.dispatcher)
    }

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun aPaidStudentSeesTheContractWithEveryPaymentAndTheDebt() = runTest(main.dispatcher) {
        harness.paid("payments-debt.json")

        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        val contracts = (state.content as TuitionContent.Shown).contracts
        assertEquals(
            ContractCard(
                id = "00-00000010",
                title = UiText.Res(CoreStrings.contract_kind_tuition),
                subtitle = UiText.Res(CoreStrings.contract_subtitle, listOf("T-0000-0010", "1 сентября 2026", "31 августа 2027")),
                balance = BalanceLine(UiText.Res(CoreStrings.contract_debt), rubles("60${NBSP}000,50"), PaymentTone.DEBT),
                pay = true,
                payments = listOf(
                    PaymentRow(
                        "Сентябрь 2026",
                        rubles("60${NBSP}000,50"),
                        due("15 сентября"),
                        paid = null,
                        pill = PaymentPill(UiText.Res(CoreStrings.payment_paid), PaymentTone.DONE),
                    ),
                    PaymentRow(
                        "Ноябрь 2026",
                        rubles("60${NBSP}000,50"),
                        due("1 ноября"),
                        paid = null,
                        pill = PaymentPill(UiText.Res(CoreStrings.payment_overdue), PaymentTone.DEBT),
                    ),
                    PaymentRow("Февраль 2027", rubles("60${NBSP}000"), due("15 февраля 2027"), paid = null, pill = null),
                ),
            ),
            contracts.current.single(),
        )
        assertNull(contracts.pastYears)
        assertNull(state.stale)
        assertEquals(listOf(FinancesHarness.AVAILABILITY, FinancesHarness.PAYMENTS), harness.paths())
    }

    @Test
    fun anOverpaymentAPaymentDueWithinAWeekAndEarlierYearsUnderPastYears() = runTest(main.dispatcher) {
        harness.paid("payments-two-years.json")

        val viewModel = viewModel().also { advanceUntilIdle() }
        val contracts = (viewModel.uiState.value.content as TuitionContent.Shown).contracts

        val card = contracts.current.single()
        assertEquals("00-00000012", card.id)
        assertEquals(BalanceLine(UiText.Res(CoreStrings.contract_overpaid), rubles("250,75"), PaymentTone.DONE), card.balance)
        assertEquals(
            PaymentRow(
                "Ноябрь 2026",
                rubles("120${NBSP}000"),
                due("13 ноября"),
                paid = UiText.Res(CoreStrings.payment_paid_part, listOf(rubles("20${NBSP}000"))),
                pill = PaymentPill(UiText.Plural(CorePlurals.payment_due_in_days, 5, listOf(5)), PaymentTone.ATTENTION),
            ),
            card.payments.last(),
        )
        val past = checkNotNull(contracts.pastYears)
        assertEquals(false, past.expanded)
        assertEquals(listOf(UiText.Res(CoreStrings.contract_academic_year, listOf("2025", "2026"))), past.years.map { it.title })
        assertEquals(false, past.years.single().contracts.single().pay)

        viewModel.togglePastYears()
        advanceUntilIdle()
        assertEquals(true, (viewModel.uiState.value.content as TuitionContent.Shown).contracts.pastYears?.expanded)
    }

    @Test
    fun aBudgetStudentSeesThatThereIsNoContract() = runTest(main.dispatcher) {
        harness.budget()

        assertEquals(TuitionContent.NoContracts, viewModel().also { advanceUntilIdle() }.uiState.value.content)
    }

    @Test
    fun offlineAfterARestartTheStoredAnswerShowsWithItsAge() = runTest(main.dispatcher) {
        harness.paid("payments-debt.json")
        viewModel().also { advanceUntilIdle() }

        harness.offline = true
        harness.clock.advanceBy(2.hours)
        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        val card = (state.content as TuitionContent.Shown).contracts.current.single()
        assertEquals(rubles("60${NBSP}000,50"), card.balance.amount)
        assertEquals(3, card.payments.size)
        assertEquals(UiText.Res(CoreStrings.common_updated_hours, listOf(2)), state.stale)
    }

    @Test
    fun anItmoErrorKeepsTheLastAnswerAndAPullThatWorksDropsTheAge() = runTest(main.dispatcher) {
        harness.paid("payments-debt.json")
        viewModel().also { advanceUntilIdle() }
        harness.paid("error.json")
        harness.clock.advanceBy(3.days)

        val viewModel = viewModel().also { advanceUntilIdle() }
        assertEquals(UiText.Plural(CorePlurals.common_updated_days, 3, listOf(3)), viewModel.uiState.value.stale)
        assertTrue(viewModel.uiState.value.content is TuitionContent.Shown)

        harness.budget()
        viewModel.refresh()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.stale)
        assertEquals(TuitionContent.NoContracts, viewModel.uiState.value.content)
    }

    @Test
    fun anAnswerMyItmoApiCannotReadFailsWithoutAnyAmount() = runTest(main.dispatcher) {
        harness.paid("payments-garbled.json")

        val failure = harness.repository().refresh() as AppResult.Failure
        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        assertTrue(failure.error is AppError.Unknown)
        assertFalse("1500" in failure.error.toString())
        assertFalse("T-0000-0011" in failure.error.toString())
        assertEquals(TuitionContent.Failed(failure.error.toUiText()), state.content)
    }

    @Test
    fun anAnswerThatCannotBeStoredIsAFailure() = runTest(main.dispatcher) {
        harness.paid("payments-debt.json")
        // A directory where the new file goes: the write fails as a full or broken disk would.
        harness.files.createDirectories("/no-backup/finances/tuition.json.new".toPath())

        val failure = harness.repository().refresh() as AppResult.Failure

        assertTrue(failure.error is AppError.Unknown)
    }

    @Test
    fun withNothingStoredAFailureIsTheScreenAndARetryLoads() = runTest(main.dispatcher) {
        harness.offline = true
        val viewModel = viewModel().also { advanceUntilIdle() }
        assertEquals(TuitionContent.Failed(AppError.Network.toUiText()), viewModel.uiState.value.content)

        harness.offline = false
        harness.paid("payments-debt.json")
        viewModel.refresh(RefreshMode.Force)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.content is TuitionContent.Shown)
    }

    @Test
    fun signOutLeavesNothingOnTheDevice() = runTest(main.dispatcher) {
        harness.paid("payments-debt.json")
        val repository = harness.repository()
        repository.refresh()

        repository.clearSessionData()
        harness.offline = true
        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        assertEquals(TuitionContent.Failed(AppError.Network.toUiText()), state.content)
    }

    @Test
    fun anAnswerThatLandsAfterSignOutIsDropped() = runTest(main.dispatcher) {
        harness.paid("payments-debt.json")
        val repository = harness.repository()
        harness.beforeAnswer = { repository.clearSessionData() }

        repository.refresh()
        harness.offline = true
        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        assertEquals(TuitionContent.Failed(AppError.Network.toUiText()), state.content)
    }

    @Test
    fun anotherAccountsAnswerIsNotShown() = runTest(main.dispatcher) {
        harness.paid("payments-debt.json")
        viewModel().also { advanceUntilIdle() }

        harness.isu = 100002
        harness.offline = true
        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        assertEquals(TuitionContent.Failed(AppError.Network.toUiText()), state.content)
    }

    private fun viewModel() = TuitionViewModel(harness.repository(), harness.time)

    private fun rubles(digits: String) = UiText.Res(CoreStrings.payment_money, listOf(digits))

    private fun due(day: String) = UiText.Res(CoreStrings.payment_due, listOf(day))

    private companion object {
        const val NBSP = "\u00A0"
    }
}
