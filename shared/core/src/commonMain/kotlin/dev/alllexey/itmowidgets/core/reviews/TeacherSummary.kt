package dev.alllexey.itmowidgets.core.reviews

/**
 * Backend's AI summary of a teacher's reviews. [reviewCount] is the number of reviews the shown summary was built
 * from and may lag behind the current reviews until a new one is built. [tags] hold only codes this app knows;
 * [scales] come in [SummaryScaleKind] order. The tone [level] is shown only when [showsLevel].
 */
data class TeacherSummary(
    val reviewCount: Int,
    val description: String,
    val pros: List<String>,
    val cons: List<String>,
    val tags: List<SummaryTag>,
    val scales: List<SummaryScale>,
    val level: TeacherLevel,
    val confidence: SummaryConfidence,
) {
    val showsLevel: Boolean get() = confidence != SummaryConfidence.LOW
}

/** [reason] is null when [value] is [SummaryScaleValue.NOT_ENOUGH_DATA]. */
data class SummaryScale(val kind: SummaryScaleKind, val value: SummaryScaleValue, val reason: String?)

/** The overall tone of a teacher's reviews, from the most negative to the most positive. */
enum class TeacherLevel { VERY_NEGATIVE, NEGATIVE, MIXED, POSITIVE, VERY_POSITIVE }

enum class SummaryConfidence { LOW, MEDIUM, HIGH }

enum class SummaryScaleKind { EXPLAINS, ATTITUDE, FAIRNESS, STRICTNESS, WORKLOAD }

enum class SummaryScaleValue { LOW, MEDIUM, HIGH, NOT_ENOUGH_DATA }

enum class SummaryTag {
    AUTOMAT,
    MANY_LABS,
    HEAVY_HOMEWORK,
    FREQUENT_TESTS,
    STRICT_DEFENSE,
    SOFT_DEFENSE,
    HARD_EXAM,
    EASY_EXAM,
    ASKS_THEORY,
    STRICT_DEADLINES,
    FLEXIBLE_DEADLINES,
    ATTENDANCE_REQUIRED,
    ATTENDANCE_OPTIONAL,
    BONUS_POINTS,
    CLEAR_REQUIREMENTS,
    UNCLEAR_REQUIREMENTS,
    INTERESTING_CLASSES,
    READS_SLIDES,
    QUICK_REPLIES,
    HARD_TO_REACH,
}
