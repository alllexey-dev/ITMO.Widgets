package dev.alllexey.itmowidgets.feature.me.moderation.domain

import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/** What a moderation decision did to the user's link or review. */
enum class NoticeOutcome { APPROVED, REJECTED, HIDDEN, RESTORED }

/** The link a notice is about: its subject period and, of the decided revision, its title and host. */
data class NoticeLink(val subjectId: Long, val subjectName: String, val periodKey: String, val title: String?, val host: String) {
    val args: SubjectLinksArgs get() = SubjectLinksArgs(subjectId, subjectName, periodKey)
}

/** The review a notice is about: its teacher and, of the decided revision, its subject. */
data class NoticeReview(val teacherIsu: Int, val teacherName: String?, val subjectTitle: String?)

/**
 * Backend's notice of a moderation decision on the user's own link or review (`docs/features/notifications.md`,
 * Moderation): [id] is the decision's, exactly one of [link] and [review] is set, [note] is a rejection's reason. The
 * bell and the Android push show the same [title] and [text].
 */
data class ModerationNotice(
    val id: String,
    val outcome: NoticeOutcome,
    val note: String?,
    val decidedAt: Instant,
    val link: NoticeLink?,
    val review: NoticeReview?,
) {
    /** A rejection or a hiding: the content is not shown to others. */
    val failed: Boolean get() = outcome == NoticeOutcome.REJECTED || outcome == NoticeOutcome.HIDDEN

    /** `Ссылка отклонена`, `Отзыв опубликован`, ... */
    val title: UiText
        get() = UiText.Res(
            if (link != null) {
                when (outcome) {
                    NoticeOutcome.APPROVED -> CoreStrings.notification_moderation_link_approved
                    NoticeOutcome.REJECTED -> CoreStrings.notification_moderation_link_rejected
                    NoticeOutcome.HIDDEN -> CoreStrings.notification_moderation_link_hidden
                    NoticeOutcome.RESTORED -> CoreStrings.notification_moderation_link_restored
                }
            } else {
                when (outcome) {
                    NoticeOutcome.APPROVED -> CoreStrings.notification_moderation_review_approved
                    NoticeOutcome.REJECTED -> CoreStrings.notification_moderation_review_rejected
                    NoticeOutcome.HIDDEN -> CoreStrings.notification_moderation_review_hidden
                    NoticeOutcome.RESTORED -> CoreStrings.notification_moderation_review_restored
                }
            },
        )

    /**
     * The link's title (else its host) and subject, or the review's teacher (else its subject); a rejection adds the
     * moderator's reason. The same keys and arguments as Backend's iOS alert.
     */
    val text: UiText
        get() {
            val reason = note?.trim()?.takeIf { it.isNotEmpty() && outcome == NoticeOutcome.REJECTED }
            link?.let { link ->
                val title = link.title?.trim()?.takeIf { it.isNotEmpty() } ?: link.host
                return if (reason != null) {
                    UiText.Res(CoreStrings.notification_moderation_link_body_note, listOf(title, link.subjectName, reason))
                } else {
                    UiText.Res(CoreStrings.notification_moderation_link_body, listOf(title, link.subjectName))
                }
            }
            val name = review?.let { it.teacherName ?: it.subjectTitle }?.trim()?.takeIf { it.isNotEmpty() }
            return when {
                name != null && reason != null -> UiText.Res(CoreStrings.notification_moderation_review_body_note, listOf(name, reason))
                name != null -> UiText.Res(CoreStrings.notification_moderation_review_body, listOf(name))
                reason != null -> UiText.Res(CoreStrings.notification_moderation_review_note, listOf(reason))
                else -> UiText.Res(CoreStrings.notification_moderation_review_unnamed)
            }
        }

    /** The link's subject links or the teacher's profile (its reviews) over [tab]. */
    fun route(tab: AppTab): EntryRoute? = when {
        link != null -> link.args.validOrNull()?.let { EntryRoute(tab, overlay = AppRoutes.SubjectLinks(it)) }
        review != null -> review.teacherIsu.takeIf { it > 0 }?.let { EntryRoute(tab, overlay = AppRoutes.UserProfile(it)) }
        else -> null
    }
}

/**
 * The user's moderation notices of the last 30 days, newest first, from Backend; null until the first answer. Kept in
 * memory and dropped with the session.
 */
interface ModerationNoticesRepository {
    val notices: StateFlow<List<ModerationNotice>?>

    /** Asks Backend; without the opt-in or in the demo it fails with that error and keeps nothing. */
    suspend fun refresh(): AppResult<List<ModerationNotice>>

    /** A pushed notice joins the list at once, so the bell shows it before the next answer. */
    fun received(notice: ModerationNotice)
}
