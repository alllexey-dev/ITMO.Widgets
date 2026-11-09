package dev.alllexey.itmowidgets.core.payments.check

import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.payments.Contract
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import kotlin.time.Instant

/**
 * One feature's part of the check `Общежитие и оплата` (`docs/features/notifications.md`): the dormitory and the
 * tuition each bind one under their own qualifier, and [PaymentsCheck] runs them all with one cadence and one
 * switch, so the features never import each other. A part writes its own bell events when its stored answer changes.
 */
interface PaymentsCheckPart {
    /** `dormitory`, `tuition`: the prefix of its Платежи reminders' keys. */
    val feature: String

    /** Where a tap on a reminder of its Платежи leads. */
    val destination: NotificationDestination

    /** The stored answer, from the device only; null before the first one. */
    suspend fun stored(): PaymentsCheckState?

    /** Asks my.itmo again and stores the answer. */
    suspend fun refresh(): AppResult<Unit>
}

/**
 * What the check reads of a part's stored answer: when it came ([fetchedAt], wall clock), whether an Этап общежития
 * is [inProgress], the part's Договоры and its own one-time [notices] (Место предоставлено).
 */
data class PaymentsCheckState(
    val fetchedAt: Instant,
    val inProgress: Boolean,
    val contracts: List<Contract>,
    val notices: List<PaymentsNotice> = emptyList(),
)

/**
 * A system notification the check posts at most once per [key]. Notices with the same [slot] share one system
 * notification, so the due day's reminder replaces the one three days before. The [text] may carry an amount; the
 * lock screen shows [publicTitle] only, so it never does.
 */
data class PaymentsNotice(
    val key: String,
    val slot: String,
    val title: UiText,
    val text: UiText,
    val publicTitle: UiText,
    val destination: NotificationDestination,
)
