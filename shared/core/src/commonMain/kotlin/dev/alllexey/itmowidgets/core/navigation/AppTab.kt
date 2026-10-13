package dev.alllexey.itmowidgets.core.navigation

import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlinx.serialization.Serializable

/**
 * The five bottom tabs in bar order: Сегодня, Расписание, Зачётка, Спорт, Профиль. [HOME] is Сегодня and [ME] is
 * Профиль; the names stay as they are because saved back stacks, entry routes and Swift name them. [HOME] is the start
 * tab: Back from another tab leads there, Back from it leaves the app.
 *
 * [label] is the catalog key of the tab's name, [icon] its Material Symbol and [selectedIcon] the FILL 1 one of the
 * selected tab; both platforms read them, so the bar says the same everywhere.
 */
@Serializable
enum class AppTab(val label: String, val icon: AppIcon, val selectedIcon: AppIcon) {
    HOME(CoreStrings.tab_today, AppIcon.TODAY, AppIcon.TODAY_FILLED),
    SCHEDULE(CoreStrings.tab_schedule, AppIcon.CALENDAR_MONTH, AppIcon.CALENDAR_MONTH_FILLED),
    RECORDBOOK(CoreStrings.tab_recordbook, AppIcon.BOOK_2, AppIcon.BOOK_2_FILLED),
    SPORT(CoreStrings.tab_sport, AppIcon.SPRINT, AppIcon.SPRINT_FILLED),
    ME(CoreStrings.tab_profile, AppIcon.PERSON, AppIcon.PERSON_FILLED);

    companion object {
        val START: AppTab = HOME
    }
}
