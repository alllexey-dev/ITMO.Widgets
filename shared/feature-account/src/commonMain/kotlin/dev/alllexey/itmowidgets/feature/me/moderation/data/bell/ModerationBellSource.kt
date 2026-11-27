package dev.alllexey.itmowidgets.feature.me.moderation.data.bell

import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.me.moderation.domain.ModerationNotice
import dev.alllexey.itmowidgets.feature.me.moderation.domain.ModerationNoticesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Backend's moderation notices in the bell: one row per decision on the user's link or review, a tap opens the
 * subject's links or the teacher's profile. [refresh] asks Backend; without the opt-in or in the demo it is quiet.
 */
class ModerationBellSource(private val notices: ModerationNoticesRepository) : BellSource {

    override fun observe(): Flow<List<BellItem>> = notices.notices.map { it.orEmpty().map(::item) }

    override suspend fun refresh(): AppResult<Unit> = when (val result = notices.refresh()) {
        is AppResult.Success -> AppResult.Success(Unit)
        is AppResult.Failure -> if (result.error in QUIET) AppResult.Success(Unit) else result
    }

    private fun item(notice: ModerationNotice) = BellItem(
        // The decision's id: a new decision on the same link is a new row.
        id = "moderation:${notice.id}",
        kind = if (notice.link != null) BellKind.LINK_MODERATION else BellKind.REVIEW_MODERATION,
        at = notice.decidedAt,
        title = notice.title,
        text = notice.text,
        failed = notice.failed,
        target = notice.route(AppTab.HOME),
    )

    private companion object {
        val QUIET = setOf(AppError.CustomServicesDisabled, AppError.DemoUnavailable)
    }
}
