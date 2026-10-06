package dev.alllexey.itmowidgets.core.navigation

import kotlinx.serialization.Serializable

/**
 * The bottom tabs in bar order. [HOME] is the start tab: Back from another tab leads there, Back from it leaves the
 * app. The labels are `title_recordbook`, `title_schedule`, `title_home`, `title_sport` and `title_me`.
 */
@Serializable
enum class AppTab {
    RECORDBOOK,
    SCHEDULE,
    HOME,
    SPORT,
    ME;

    companion object {
        val START: AppTab = HOME
    }
}
