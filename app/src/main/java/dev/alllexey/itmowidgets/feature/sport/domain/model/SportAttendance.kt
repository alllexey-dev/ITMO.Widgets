package dev.alllexey.itmowidgets.feature.sport.domain.model

import kotlin.time.Instant

data class SportAttendance(
    // lesson, exercise, competition (and probably others?)
    val type: String,
    // can be null, does not match section name sometimes (for exercise)
    val name: SectionName?,
    val evaluationId: Long,
    // can be empty/null
    val evaluationName: String?,
    val sectionLevel: Int,
    val score: Int,
    val dateTime: Instant,
    // can be false even if type = "competition", lol
    val isCompetition: Boolean
)
