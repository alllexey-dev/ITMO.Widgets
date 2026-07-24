package dev.alllexey.itmowidgets.feature.sport.domain.model

import kotlin.math.max
import kotlin.math.min

data class SportScore(
    val attendances: Int,
    val other: Int,
    val attendancesData: List<SportAttendance>
) {
    val otherCapped = min(40, other)
    val total = attendances + otherCapped
    val totalCapped = min(100, total)
    val need = max(0, 100 - totalCapped)
}
