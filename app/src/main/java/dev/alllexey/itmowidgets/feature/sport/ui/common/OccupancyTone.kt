package dev.alllexey.itmowidgets.feature.sport.ui.common

import dev.alllexey.itmowidgets.core.ui.ConditionTone

fun occupancyTone(available: Int, limit: Int): ConditionTone = when {
    available == 0 -> ConditionTone.BLOCKED
    available.toDouble() / limit <= 0.2 -> ConditionTone.WARNING
    else -> ConditionTone.WAITING
}
