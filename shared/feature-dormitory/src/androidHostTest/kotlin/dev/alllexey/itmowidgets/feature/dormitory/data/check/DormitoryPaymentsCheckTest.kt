package dev.alllexey.itmowidgets.feature.dormitory.data.check

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
import dev.alllexey.itmowidgets.feature.dormitory.DormitoryHarness
import dev.alllexey.itmowidgets.feature.dormitory.data.bell.DormitoryBellSource
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import okio.Path.Companion.toPath

/**
 * The check `Общежитие и оплата` with the dormitory's part over the real MyItmoApi on a `MockEngine` and a fixed
 * clock (12:00 Moscow on 8 November at the start): which notifications and bell events each transition brings, the
 * cadence, the quiet hours, the switch and the demo.
 */
class DormitoryPaymentsCheckTest {

    private val dispatcher = StandardTestDispatcher()
    private var harness = DormitoryHarness(dispatcher)
    private var notifier = RecordingPaymentsNotifier()
    private val preferences = PaymentsCheckPreferences(InMemoryPreferencesDataStore())
    private val sessionTokens = FakeSessionTokenStore()
    private lateinit var log: PaymentsNoticeLog
    private lateinit var check: PaymentsCheck

    init {
        build()
    }

    @Test
    fun aPlaceOfferedIsNotifiedOnFirstSightAndOnlyOnce() = runTest(dispatcher) {
        harness.answer("status-offered.json", documents = "documents.json")

        assertEquals(CheckOutcome.DONE, check.run())
        harness.clock.advanceBy(3.hours)
        assertEquals(CheckOutcome.DONE, check.run())

        assertEquals(listOf(PLACE_OFFERED), notifier.shown.withoutIds())
        // The first answer is a baseline: the dormitory it names was not "assigned" just now.
        assertEquals(emptyList(), bell())
    }

    @Test
    fun aPlaceOfferedAgainAfterTheStageLeftIsNotifiedAgain() = runTest(dispatcher) {
        harness.answer("status-offered.json", documents = "documents.json")
        check.run()
        harness.clock.advanceBy(3.hours)
        harness.answer("status-date-chosen.json")
        check.run()
        harness.clock.advanceBy(3.hours)
        harness.answer("status-offered.json")
        check.run()

        assertEquals(listOf(PLACE_OFFERED, PLACE_OFFERED), notifier.shown.withoutIds())
    }

    @Test
    fun aPaymentIsRemindedThreeDaysBeforeAndOnTheDueDayEachOnce() = runTest(dispatcher) {
        settledWith("contracts-clear.json")

        check.run()
        assertEquals(emptyList(), notifier.shown, "five days ahead")

        advanceTo(LocalDateTime(2026, 11, 10, 12, 0))
        check.run()
        harness.clock.advanceBy(3.hours)
        check.run()
        advanceTo(LocalDateTime(2026, 11, 13, 9, 0))
        check.run()
        harness.clock.advanceBy(3.hours)
        check.run()
        advanceTo(LocalDateTime(2026, 11, 14, 12, 0))
        check.run()

        assertEquals(listOf(reminder(inDays(3)), reminder(TODAY)), notifier.shown.withoutIds())
        // One system notification per Платёж: the due day's replaces the earlier one.
        assertEquals(1, notifier.shown.map { it.id }.distinct().size)
    }

    @Test
    fun aPaymentFirstSeenTwoDaysAheadIsRemindedAtOnce() = runTest(dispatcher) {
        advanceTo(LocalDateTime(2026, 11, 11, 12, 0))
        settledWith("contracts-clear.json")

        check.run()

        assertEquals(listOf(reminder(inDays(2))), notifier.shown.withoutIds())
    }

    @Test
    fun aDebtBringsNoReminder() = runTest(dispatcher) {
        advanceTo(LocalDateTime(2026, 11, 9, 12, 0))
        settledWith("contracts-debt.json")

        check.run()
        harness.clock.advanceBy(3.hours)
        check.run()

        assertEquals(emptyList(), notifier.shown)
    }

    @Test
    fun inTheQuietHoursAndroidHoldsTheNoticeUntilTheFirstRunAfterSix() = runTest(dispatcher) {
        advanceTo(LocalDateTime(2026, 11, 10, 3, 0))
        settledWith("contracts-clear.json")

        check.run()
        assertEquals(emptyList(), notifier.shown)

        harness.clock.advanceBy(3.hours)
        check.run()
        assertEquals(listOf(reminder(inDays(3))), notifier.shown.withoutIds())
    }

    @Test
    fun inTheQuietHoursIosHandsTheNoticeToTheSystemForSixOnce() = runTest(dispatcher) {
        notifier = RecordingPaymentsNotifier(canSchedule = true)
        build()
        advanceTo(LocalDateTime(2026, 11, 10, 3, 0))
        settledWith("contracts-clear.json")

        check.run()
        harness.clock.advanceBy(3.hours)
        check.run()

        assertEquals(listOf(Instant.parse("2026-11-10T03:00:00Z")), notifier.scheduled.map { it.second })
        assertEquals(listOf(reminder(inDays(3))), notifier.scheduled.map { it.first.copy(id = 0) })
        assertEquals(emptyList(), notifier.shown)
    }

    @Test
    fun theQueuePlaceThatMovedIsInTheBell() = runTest(dispatcher) {
        harness.answer("status-queue.json")
        check.run()
        harness.clock.advanceBy(3.hours)
        harness.answer("status-queue-moved.json")
        check.run()
        harness.clock.advanceBy(3.hours)
        check.run()

        assertEquals(
            listOf(
                BellItem(
                    id = "dormitory:queue:${harness.clock.now().minus(3.hours).toEpochMilliseconds()}",
                    kind = BellKind.DORMITORY,
                    at = harness.clock.now() - 3.hours,
                    title = UiText.Res(DormitoryStrings.dormitory_bell_queue),
                    was = UiText.Dynamic("42"),
                    now = UiText.Dynamic("37"),
                    target = TARGET,
                ),
            ),
            bell(),
        )
        assertEquals(emptyList(), notifier.shown)
    }

    @Test
    fun anAssignedDormitoryIsInTheBellBesideThePlaceOffered() = runTest(dispatcher) {
        harness.answer("status-queue.json")
        check.run()
        harness.clock.advanceBy(3.hours)
        harness.answer("status-offered.json", documents = "documents.json")
        check.run()

        val item = bell().single()
        assertEquals(BellKind.DORMITORY, item.kind)
        assertEquals(UiText.Res(DormitoryStrings.dormitory_bell_assigned), item.title)
        assertEquals(UiText.Dynamic("Синтетическое общежитие"), item.text)
        assertEquals(listOf(PLACE_OFFERED), notifier.shown.withoutIds())
    }

    @Test
    fun aPaymentThatBecamePaidIsInTheBell() = runTest(dispatcher) {
        settledWith("contracts-clear.json")
        check.run()
        harness.clock.advanceBy(3.hours)
        settledWith("contracts-clear-paid.json")
        check.run()

        val item = bell().single()
        assertEquals(BellKind.PAYMENT, item.kind)
        assertEquals(UiText.Res(CoreStrings.payment_bell_paid), item.title)
        assertEquals(
            UiText.Joined(
                listOf(UiText.Res(CoreStrings.contract_kind_rent), UiText.Dynamic("Ноябрь 2026"), PaymentTexts.money(Money(150_000))),
                " · ",
            ),
            item.text,
        )
        assertEquals(TARGET, item.target)
    }

    @Test
    fun withTheSwitchOffNothingIsPostedAndTheBellStillFills() = runTest(dispatcher) {
        preferences.setNotificationsEnabled(false)
        harness.answer("status-queue.json")
        check.run()
        harness.clock.advanceBy(3.hours)
        harness.answer("status-offered.json", documents = "documents.json")
        check.run()

        assertEquals(emptyList(), notifier.shown)
        assertEquals(listOf(BellKind.DORMITORY), bell().map { it.kind })
    }

    @Test
    fun anApplicationIsAskedAboutEveryRun() = runTest(dispatcher) {
        harness.answer("status-settled.json")
        check.run()
        val first = harness.requests.size

        harness.clock.advanceBy(3.hours)
        assertEquals(CheckOutcome.DONE, check.run())

        assertEquals(2 * first, harness.requests.size)
    }

    @Test
    fun withNothingPendingTheCheckAsksOnceADay() = runTest(dispatcher) {
        harness.answer("status-empty.json")
        assertEquals(CheckOutcome.DONE, check.run())
        val first = harness.requests.size

        harness.clock.advanceBy(21.hours)
        assertEquals(CheckOutcome.SKIPPED, check.run())
        assertEquals(first, harness.requests.size)

        harness.clock.advanceBy(3.hours)
        assertEquals(CheckOutcome.DONE, check.run())
        assertEquals(2 * first, harness.requests.size)
    }

    @Test
    fun aPaidUpContractWithoutAnApplicationIsAlsoAskedOnceADay() = runTest(dispatcher) {
        harness.answer("status-empty.json")
        harness.contracts("periods-current.json", mapOf("2026-01-01" to "contracts-clear-paid.json"))
        check.run()

        harness.clock.advanceBy(3.hours)

        assertEquals(CheckOutcome.SKIPPED, check.run())
    }

    @Test
    fun offlineTheRemindersComeFromTheStoredAnswer() = runTest(dispatcher) {
        settledWith("contracts-clear.json")
        check.run()

        harness.offline = true
        advanceTo(LocalDateTime(2026, 11, 10, 12, 0))

        assertEquals(CheckOutcome.RETRY, check.run())
        assertEquals(listOf(reminder(inDays(3))), notifier.shown.withoutIds())
    }

    @Test
    fun signOutForgetsWhatWasPosted() = runTest(dispatcher) {
        harness.answer("status-offered.json", documents = "documents.json")
        check.run()

        log.clearSessionData()
        harness.clock.advanceBy(3.hours)
        check.run()

        assertEquals(listOf(PLACE_OFFERED, PLACE_OFFERED), notifier.shown.withoutIds())
    }

    @Test
    fun withoutASessionNothingRuns() = runTest(dispatcher) {
        sessionTokens.signedIn = false
        harness.answer("status-offered.json", documents = "documents.json")

        assertEquals(CheckOutcome.SKIPPED, check.run())

        assertEquals(emptyList(), harness.requests)
        assertEquals(emptyList(), notifier.shown)
    }

    @Test
    fun theDemoSendsNothingAndPostsNothing() = runTest(dispatcher) {
        harness = DormitoryHarness(dispatcher, demo = true)
        build()
        harness.repository().refresh()

        assertEquals(CheckOutcome.SKIPPED, check.run())

        assertEquals(emptyList(), harness.requests)
        assertTrue(notifier.shown.isEmpty() && notifier.scheduled.isEmpty())
    }

    private fun build() {
        val repository = harness.repository()
        log = PaymentsNoticeLog.over(AtomicTextFile("/no-backup/payments_notices.json".toPath(), harness.files), harness.clock, harness.dispatchers)
        check = PaymentsCheck(
            parts = listOf(DormitoryCheckPart(repository)),
            preferences = preferences,
            log = log,
            notifier = notifier,
            sessionTokens = sessionTokens,
            demo = harness.demoMode,
            clock = harness.clock,
            time = harness.time,
        )
    }

    private fun settledWith(contracts: String) {
        harness.answer("status-settled.json")
        harness.contracts("periods-current.json", mapOf("2026-01-01" to contracts))
    }

    /** Moves the clock forward to [moscow]. */
    private fun advanceTo(moscow: LocalDateTime) {
        harness.clock.advanceBy(moscow.toInstant(harness.time.timeZone) - harness.clock.now())
    }

    private suspend fun bell(): List<BellItem> = DormitoryBellSource(harness.bell).observe().first()

    private fun List<AppNotification>.withoutIds() = map { it.copy(id = 0) }

    private fun inDays(days: Int): UiText = UiText.Plural(CorePlurals.payment_reminder_in_days, days, listOf(days))

    /** The reminder of the November Платёж of `contracts-clear.json`: 1 000 ₽ left of 1 500 ₽. */
    private fun reminder(title: UiText) = AppNotification(
        channel = AppNotificationChannels.DORMITORY_PAYMENTS,
        id = 0,
        title = title,
        text = UiText.Joined(
            listOf(UiText.Res(CoreStrings.contract_kind_rent), UiText.Dynamic("Ноябрь 2026"), PaymentTexts.money(Money(100_000))),
            " · ",
        ),
        destination = NotificationDestination.Dormitory,
        publicTitle = title,
    )

    private companion object {
        val TODAY = UiText.Res(CoreStrings.payment_reminder_today)
        val TARGET = EntryRoute(AppTab.HOME, overlay = AppRoutes.Dormitory)
        val PLACE_OFFERED = AppNotification(
            channel = AppNotificationChannels.DORMITORY_PAYMENTS,
            id = 0,
            title = UiText.Res(DormitoryStrings.dormitory_notification_offered_title),
            text = UiText.Res(DormitoryStrings.dormitory_place_offered_text),
            destination = NotificationDestination.Dormitory,
            publicTitle = UiText.Res(DormitoryStrings.dormitory_notification_offered_title),
        )
    }
}
