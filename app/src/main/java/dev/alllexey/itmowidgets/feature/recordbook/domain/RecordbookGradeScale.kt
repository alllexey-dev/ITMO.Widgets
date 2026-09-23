package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookAssessmentKind

/** The next reachable result and the points still missing for it. */
data class GradeStep(val target: String, val remaining: Double)

/** ITMO 100-point scale; a plain credit has the single 60-point threshold. */
object RecordbookGradeScale {
    const val CREDIT_TARGET = "зачёт"
    private const val PASS_SCORE = 60.0

    /** Hints count whole points, so a strict "> N" threshold is aimed at N + 1. */
    private class Grade(val code: String, val threshold: Double, val inclusive: Boolean) {
        fun isReachedBy(score: Double): Boolean = if (inclusive) score >= threshold else score > threshold
        fun remainingFrom(score: Double): Double = (if (inclusive) threshold else threshold + 1) - score
    }

    private val grades = listOf(
        Grade("5A", 90.0, inclusive = false),
        Grade("4B", 83.0, inclusive = false),
        Grade("4C", 74.0, inclusive = false),
        Grade("3D", 67.0, inclusive = false),
        Grade("3E", PASS_SCORE, inclusive = true)
    )

    /** `null` means the score is unsatisfactory. */
    fun gradeFor(score: Double): String? = grades.firstOrNull { it.isReachedBy(score) }?.code

    fun nextStep(score: Double?, kind: RecordbookAssessmentKind): GradeStep? {
        if (score == null) return null
        return when (kind) {
            RecordbookAssessmentKind.CREDIT ->
                if (score < PASS_SCORE) GradeStep(CREDIT_TARGET, PASS_SCORE - score) else null
            RecordbookAssessmentKind.EXAM,
            RecordbookAssessmentKind.GRADED_CREDIT,
            RecordbookAssessmentKind.OTHER ->
                grades.lastOrNull { !it.isReachedBy(score) }?.let { GradeStep(it.code, it.remainingFrom(score)) }
        }
    }
}
