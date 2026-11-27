package dev.alllexey.itmowidgets.feature.me.moderation.data

import dev.alllexey.itmowidgets.client.moderation.ModerationNoticesApi
import dev.alllexey.itmowidgets.client.moderation.ModerationOutcome
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.me.moderation.domain.ModerationNotice
import dev.alllexey.itmowidgets.feature.me.moderation.domain.ModerationNoticesRepository
import dev.alllexey.itmowidgets.feature.me.moderation.domain.NoticeLink
import dev.alllexey.itmowidgets.feature.me.moderation.domain.NoticeOutcome
import dev.alllexey.itmowidgets.feature.me.moderation.domain.NoticeReview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import dev.alllexey.itmowidgets.client.moderation.ModerationNotice as WireNotice

/** `GET /api/users/me/moderation-notices` behind the demo switch and the opt-in; the last answer stays in memory. */
class ModerationNoticesRepositoryImpl(
    private val backend: BackendGate,
    private val api: ModerationNoticesApi,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : ModerationNoticesRepository, SessionDataCleaner {
    private val last = MutableStateFlow<List<ModerationNotice>?>(null)
    override val notices: StateFlow<List<ModerationNotice>?> = last.asStateFlow()

    /** The demo session first, then the opt-in, then the call on IO. */
    override suspend fun refresh(): AppResult<List<ModerationNotice>> {
        if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
        if (!backend.mayCallBackend()) return AppResult.Failure(AppError.CustomServicesDisabled)
        return appResultOf(Exception::toAppError) { withContext(dispatchers.io) { api.notices().mapNotNull { it.toDomain() } } }
            .also { if (it is AppResult.Success) last.value = it.value }
    }

    override fun received(notice: ModerationNotice) {
        last.update { current -> (listOf(notice) + current.orEmpty().filter { it.id != notice.id }).sortedByDescending { it.decidedAt } }
    }

    override suspend fun clearSessionData() {
        last.value = null
    }
}

/** A notice this build can show: a known outcome about a link or a review; anything else is null. */
fun WireNotice.toDomain(): ModerationNotice? {
    val outcome = when (outcome) {
        ModerationOutcome.APPROVED -> NoticeOutcome.APPROVED
        ModerationOutcome.REJECTED -> NoticeOutcome.REJECTED
        ModerationOutcome.HIDDEN -> NoticeOutcome.HIDDEN
        ModerationOutcome.RESTORED -> NoticeOutcome.RESTORED
        ModerationOutcome.UNKNOWN -> return null
    }
    val link = link?.let { NoticeLink(it.subjectId, it.subjectName.trim(), it.periodKey, it.title, it.host) }
    val review = review?.let { NoticeReview(it.teacherIsu, it.teacherName?.trim()?.ifEmpty { null }, it.subjectTitle?.trim()?.ifEmpty { null }) }
    if ((link == null) == (review == null)) return null
    return ModerationNotice(id.toHexDashString(), outcome, note, decidedAt, link, review)
}
