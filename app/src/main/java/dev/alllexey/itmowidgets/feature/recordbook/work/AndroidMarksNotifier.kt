package dev.alllexey.itmowidgets.feature.recordbook.work

import android.content.Context
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
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkNewsTexts
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.toSubjectArgs
import dev.alllexey.itmowidgets.shared.core.CoreStrings

/**
 * One subject with numbers says them in the title (`Матанализ: 9 из 10`) and the details under it; several are
 * "Новые оценки" with the subject names. The lock screen shows "Новые оценки" alone. One subject with a page opens
 * it, anything else opens the recordbook.
 */
class AndroidMarksNotifier(
    private val context: Context,
    private val notifier: AppNotifier
) : MarksNotifier {

    override fun showDigest(digest: MarkDigest, target: MarkSubjectTarget?) {
        val title = UiText.Res(CoreStrings.marks_new_title)
        val single = digest.single
        notifier.show(AppNotification(
            channel = AppNotificationChannels.MARKS,
            id = MarkDigests.DIGEST_ID,
            title = single?.let(MarkNewsTexts::notificationTitle) ?: title,
            text = single?.takeIf { it.value != null }?.let(MarkNewsTexts::details)
                ?: UiText.Dynamic(markSubjectList(context, digest.subjects)),
            destination = target?.toSubjectArgs()?.let(NotificationDestination::RecordbookSubject)
                ?: NotificationDestination.Recordbook,
            publicTitle = title
        ))
    }

    override fun showBarsPrompt() {
        val title = UiText.Res(CoreStrings.marks_bars_login_title)
        notifier.show(AppNotification(
            channel = AppNotificationChannels.MARKS,
            id = MarkDigests.PROMPT_ID,
            title = title,
            text = UiText.Res(CoreStrings.marks_bars_login_text),
            destination = NotificationDestination.BarsLogin,
            publicTitle = title
        ))
    }
}
