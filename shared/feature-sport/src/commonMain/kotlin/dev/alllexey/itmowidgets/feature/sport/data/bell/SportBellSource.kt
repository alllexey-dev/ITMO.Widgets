package dev.alllexey.itmowidgets.feature.sport.data.bell

import dev.alllexey.itmoapi.core.MOSCOW
import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/** The queues' results in the bell: `Волейбол: вы записаны` or `не удалось записать`, the lesson's day and time. */
class SportBellSource(private val log: SportResultLog) : BellSource {

    override fun observe(): Flow<List<BellItem>> = log.observe().map { results -> results.map(::item) }

    private fun item(result: SportResult) = BellItem(
        id = "sport:${result.lessonId}:${result.at}",
        kind = BellKind.SPORT,
        at = result.handledAt,
        title = UiText.Res(
            if (result.booked) SportStrings.sport_bell_booked else SportStrings.sport_bell_refused,
            listOf(result.sectionName),
        ),
        text = UiText.Res(
            SportStrings.sport_bell_lesson,
            listOf(result.startsAt.toLocalDateTime(MOSCOW).format(DateTexts.SHORT_WEEKDAY_DAY_MONTH_TIME)),
        ),
        failed = !result.booked,
        target = EntryRoute(AppTab.SPORT),
    )
}
