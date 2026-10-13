package dev.alllexey.itmowidgets.core.today

import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.coroutines.flow.Flow

/** One thing to do today under the day on Сегодня: what, why, one [action] that opens [target]. */
data class TodayTodo(
    val id: String,
    val icon: AppIcon,
    val title: UiText,
    val reason: UiText,
    val action: UiText,
    val target: EntryRoute,
)

/**
 * A feature's contribution to «Нужно сделать»: an empty list when there is nothing to do. Sources are an open set
 * (`getAll()`), each under its own qualifier.
 */
fun interface TodayTodoSource {
    fun observe(): Flow<List<TodayTodo>>
}
