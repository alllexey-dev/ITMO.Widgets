package dev.alllexey.itmowidgets.feature.sport.ui.common

import androidx.annotation.StringRes
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingObstacle

@StringRes
fun SportBookingObstacle.titleRes(): Int = when (this) {
    SportBookingObstacle.TIME_CONFLICT -> R.string.sport_rule_time_conflict
    SportBookingObstacle.DAILY_LIMIT -> R.string.sport_rule_daily_limit
    SportBookingObstacle.WEEKLY_LIMIT -> R.string.sport_rule_weekly_limit
    SportBookingObstacle.CREDIT -> R.string.sport_rule_credit
    SportBookingObstacle.SELECTION -> R.string.sport_rule_selection
    SportBookingObstacle.EXTERNAT -> R.string.sport_rule_externat
    SportBookingObstacle.DEBT_ONLY -> R.string.sport_rule_debt_only
    SportBookingObstacle.DENIED -> R.string.sport_rule_denied
    SportBookingObstacle.HEALTH -> R.string.sport_rule_health
    SportBookingObstacle.STARTED -> R.string.sport_rule_started
    SportBookingObstacle.UNKNOWN -> R.string.sport_rule_unknown
}
