package dev.alllexey.itmowidgets.feature.finances.data.check

import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.payments.Money
import dev.alllexey.itmowidgets.core.payments.check.PaymentsCheck
import dev.alllexey.itmowidgets.core.payments.check.PaymentsNoticeLog
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.core.storage.PaymentsCheckPreferences
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.RecordingPaymentsNotifier
import dev.alllexey.itmowidgets.core.text.PaymentTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.feature.finances.FinancesHarness
import dev.alllexey.itmowidgets.feature.finances.data.bell.TuitionBellSource
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import okio.Path.Companion.toPath

/**
 * The check `Общежитие и оплата` with the tuition's part over the real MyItmoApi on a `MockEngine` and a fixed clock
 * (12:00 Moscow on 8 November): the Платежи reminders, the bell's paid Платёж, the cadence and the demo.
 */
class TuitionPaymentsCheckTest {

    private val dispatcher = StandardTestDispatcher()
    private var harness = FinancesHarness(dispatcher)
    private val notifier = RecordingPaymentsNotifier()
    private val preferences = PaymentsCheckPreferences(InMemoryPreferencesDataStore())
    private lateinit var check: PaymentsCheck

    init {
        build()
    }

    @Test
    fun aTuitionPaymentIsRemindedThreeDaysBeforeAndOnTheDueDayEachOnce() = runTest(dispatcher) {
        harness.paid("payments-two-years.json")

        check.run()
        advanceTo(LocalDateTime(2026, 11, 10, 12, 0))
        check.run()
        harness.clock.advanceBy(3.hours)
        check.run()
        advanceTo(LocalDateTime(2026, 11, 13, 12, 0))
        check.run()
        advanceTo(LocalDateTime(2026, 11, 14, 12, 0))
        check.run()

        assertEquals(
            listOf(reminder(UiText.Plural(CorePlurals.payment_reminder_in_days, 3, listOf(3))), reminder(UiText.Res(CoreStrings.payment_reminder_today))),
            notifier.shown.map { it.copy(id = 0) },
        )
    }

    @Test
    fun aTuitionDebtBringsNoReminder() = runTest(dispatcher) {
        harness.paid("payments-debt.json")

        check.run()
        harness.clock.advanceBy(3.hours)
        check.run()

        assertEquals(emptyList(), notifier.shown)
    }

    @Test
    fun aTuitionPaymentThatBecamePaidIsInTheBell() = runTest(dispatcher) {
        harness.paid("payments-two-years.json")
        check.run()
        harness.clock.advanceBy(3.hours)
        harness.paid("payments-two-years-paid.json")
        check.run()

        assertEquals(
            listOf(
                BellItem(
                    id = "tuition:paid:00-00000012:2026-11-13",
                    kind = BellKind.PAYMENT,
                    at = harness.clock.now(),
                    title = UiText.Res(CoreStrings.payment_bell_paid),
                    text = UiText.Joined(
                        listOf(UiText.Res(CoreStrings.contract_kind_tuition), UiText.Dynamic("Ноябрь 2026"), PaymentTexts.money(Money(12_000_000))),
                        " · ",
                    ),
                    target = EntryRoute(AppTab.HOME, overlay = AppRoutes.Tuition),
                ),
            ),
            TuitionBellSource(harness.bell).observe().first(),
        )
    }

    @Test
    fun somethingLeftToPayIsAskedAboutEveryRun() = runTest(dispatcher) {
        harness.paid("payments-two-years.json")
        check.run()
        harness.clock.advanceBy(3.hours)

        assertEquals(CheckOutcome.DONE, check.run())
    }

    @Test
    fun aBudgetPlaceIsAskedAboutOnceADay() = runTest(dispatcher) {
        harness.budget()
        assertEquals(CheckOutcome.DONE, check.run())
        val first = harness.requests.size

        harness.clock.advanceBy(21.hours)
        assertEquals(CheckOutcome.SKIPPED, check.run())
        harness.clock.advanceBy(3.hours)
        assertEquals(CheckOutcome.DONE, check.run())

        assertEquals(2 * first, harness.requests.size)
    }

    @Test
    fun theDemoSendsNothingAndPostsNothing() = runTest(dispatcher) {
        harness = FinancesHarness(dispatcher, demo = true)
        build()

        assertEquals(CheckOutcome.SKIPPED, check.run())

        assertEquals(emptyList(), harness.requests)
        assertEquals(emptyList(), notifier.shown)
    }

    private fun build() {
        check = PaymentsCheck(
            parts = listOf(TuitionCheckPart(harness.repository())),
            preferences = preferences,
            log = PaymentsNoticeLog.over(AtomicTextFile("/no-backup/payments_notices.json".toPath(), harness.files), harness.clock, harness.dispatchers),
            notifier = notifier,
            sessionTokens = FakeSessionTokenStore(),
            demo = harness.demoMode,
            clock = harness.clock,
            time = harness.time,
        )
    }

    private fun advanceTo(moscow: LocalDateTime) {
        harness.clock.advanceBy(moscow.toInstant(harness.time.timeZone) - harness.clock.now())
    }

    /** The reminder of the November Платёж of `payments-two-years.json`: 100 000 ₽ left of 120 000 ₽. */
    private fun reminder(title: UiText) = AppNotification(
        channel = AppNotificationChannels.DORMITORY_PAYMENTS,
        id = 0,
        title = title,
        text = UiText.Joined(
            listOf(UiText.Res(CoreStrings.contract_kind_tuition), UiText.Dynamic("Ноябрь 2026"), PaymentTexts.money(Money(10_000_000))),
            " · ",
        ),
        destination = NotificationDestination.Tuition,
        publicTitle = title,
    )
}
