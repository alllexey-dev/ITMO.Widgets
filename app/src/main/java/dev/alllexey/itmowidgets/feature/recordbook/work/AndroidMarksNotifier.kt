package dev.alllexey.itmowidgets.feature.recordbook.work

import android.content.Context
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.ui.markSubjectList
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigest
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigests
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSubjectTarget
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.marks_bars_login_text
import dev.alllexey.itmowidgets.shared.core.marks_bars_login_title
import dev.alllexey.itmowidgets.shared.core.marks_new_title

/**
 * "Новые оценки" with subject names only, never the marks; the lock screen shows the title alone.
 * One subject with a page opens it, anything else opens the recordbook.
 */
class AndroidMarksNotifier(
    private val context: Context,
    private val notifier: AppNotifier
) : MarksNotifier {

    override fun showDigest(digest: MarkDigest, target: MarkSubjectTarget?) {
        val title = UiText.Res(Res.string.marks_new_title)
        notifier.show(AppNotification(
            channel = AppNotificationChannels.MARKS,
            id = MarkDigests.DIGEST_ID,
            title = title,
            text = UiText.Dynamic(markSubjectList(context, digest.subjects)),
            destination = target?.toArgs()?.let(NotificationDestination::RecordbookSubject)
                ?: NotificationDestination.Recordbook,
            publicTitle = title
        ))
    }

    override fun showBarsPrompt() {
        val title = UiText.Res(Res.string.marks_bars_login_title)
        notifier.show(AppNotification(
            channel = AppNotificationChannels.MARKS,
            id = MarkDigests.PROMPT_ID,
            title = title,
            text = UiText.Res(Res.string.marks_bars_login_text),
            destination = NotificationDestination.BarsLogin,
            publicTitle = title
        ))
    }
}

private fun MarkSubjectTarget.toArgs(): RecordbookSubjectArgs? = RecordbookSubjectArgs(
    entryId = entryId,
    programId = programId,
    semester = semester,
    studyYear = studyYear,
    barsPlan = bars?.planId,
    barsType = bars?.type,
    barsIdentifier = bars?.identifier
).validOrNull()
