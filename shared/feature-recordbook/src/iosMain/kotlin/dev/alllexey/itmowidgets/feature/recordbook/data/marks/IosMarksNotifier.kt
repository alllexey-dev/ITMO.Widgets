package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.IosAppNotifier
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.markSubjectList
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigest
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigests
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSubjectTarget
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.marks_bars_login_text
import dev.alllexey.itmowidgets.shared.core.marks_bars_login_title
import dev.alllexey.itmowidgets.shared.core.marks_new_title
import kotlin.time.Instant

/** A [MarksNotifier] that can also hand its notifications to the system for a later moment. */
interface MorningMarksNotifier : MarksNotifier {
    /** Posts [digest] for [at]: marks found in the quiet hours are delivered when they end. */
    suspend fun showDigestAt(digest: MarkDigest, target: MarkSubjectTarget?, at: Instant)

    /** Posts the "sign in to BARS" reminder for [at]. */
    suspend fun showBarsPromptAt(at: Instant)
}

/**
 * The iOS [MarksNotifier] (IO-09d3), Android's `AndroidMarksNotifier` over [IosAppNotifier] on the `marks` thread:
 * "Новые оценки" with the subject names only, never the marks, and the one «Войдите в БАРС», by catalog key. A newer
 * digest replaces the shown or the scheduled one. One subject with a page leads to it, anything else to the
 * recordbook; the BARS reminder opens the BARS sign-in.
 */
class IosMarksNotifier(private val notifier: IosAppNotifier) : MorningMarksNotifier {

    override fun showDigest(digest: MarkDigest, target: MarkSubjectTarget?) =
        notifier.show(digestOf(digest, target))

    override fun showBarsPrompt() = notifier.show(barsPrompt())

    override suspend fun showDigestAt(digest: MarkDigest, target: MarkSubjectTarget?, at: Instant) =
        notifier.post(digestOf(digest, target), deliverAt = at)

    override suspend fun showBarsPromptAt(at: Instant) = notifier.post(barsPrompt(), deliverAt = at)

    private fun digestOf(digest: MarkDigest, target: MarkSubjectTarget?): AppNotification {
        val title = UiText.Res(Res.string.marks_new_title)
        return AppNotification(
            channel = AppNotificationChannels.MARKS,
            id = MarkDigests.DIGEST_ID,
            title = title,
            text = markSubjectList(digest.subjects),
            destination = target?.toArgs()?.let(NotificationDestination::RecordbookSubject)
                ?: NotificationDestination.Recordbook,
            publicTitle = title,
        )
    }

    private fun barsPrompt(): AppNotification {
        val title = UiText.Res(Res.string.marks_bars_login_title)
        return AppNotification(
            channel = AppNotificationChannels.MARKS,
            id = MarkDigests.PROMPT_ID,
            title = title,
            text = UiText.Res(Res.string.marks_bars_login_text),
            destination = NotificationDestination.BarsLogin,
            publicTitle = title,
        )
    }
}

private fun MarkSubjectTarget.toArgs(): RecordbookSubjectArgs? = RecordbookSubjectArgs(
    entryId = entryId,
    programId = programId,
    semester = semester,
    studyYear = studyYear,
    barsPlan = bars?.planId,
    barsType = bars?.type,
    barsIdentifier = bars?.identifier,
).validOrNull()
