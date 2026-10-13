package dev.alllexey.itmowidgets.feature.recordbook.data.today

import dev.alllexey.itmowidgets.core.navigation.ActivityRoute
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.today.TodayTodo
import dev.alllexey.itmowidgets.core.today.TodayTodoSource
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * «Войдите в БАРС заново» under the day while the background mark check found the ITMO.ID session behind BARS ended
 * (`BarsLoginPrompt` is not `NONE`); a successful BARS answer or switching BARS marks off removes it.
 */
class BarsTodoSource(
    private val markSources: MarkSourcePreferences,
    private val repository: MarkTrackingRepository,
    private val time: AcademicTimeProvider,
) : TodayTodoSource {

    override fun observe(): Flow<List<TodayTodo>> =
        combine(markSources.observeBarsLoginPrompt(), repository.observeBarsCheckedAt()) { prompt, checkedAt ->
            if (prompt == BarsLoginPrompt.NONE) {
                emptyList()
            } else {
                listOf(
                    TodayTodo(
                        id = ID,
                        icon = AppIcon.LOGIN,
                        title = UiText.Res(RecordbookStrings.recordbook_todo_bars_title),
                        reason = reason(checkedAt),
                        action = UiText.Res(RecordbookStrings.recordbook_todo_bars_action),
                        target = EntryRoute(AppTab.HOME, activity = ActivityRoute.BARS_LOGIN),
                    ),
                )
            }
        }

    /** Since `09:40` today, since `7 окт., 09:40` before. */
    private fun reason(checkedAt: Instant?): UiText {
        val at = checkedAt?.toLocalDateTime(time.timeZone) ?: return UiText.Res(RecordbookStrings.recordbook_todo_bars_reason)
        val since = if (at.date == time.today()) at.time.format(DateTexts.TIME) else at.format(DateTexts.DAY_SHORT_MONTH_TIME)
        return UiText.Res(RecordbookStrings.recordbook_todo_bars_reason_since, listOf(since))
    }

    private companion object {
        const val ID = "bars-sign-in"
    }
}
