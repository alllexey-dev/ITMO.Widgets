package dev.alllexey.itmowidgets.client.reviews

import dev.alllexey.itmowidgets.client.json.UnknownTolerantEnumSerializer
import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The AI summary shown in a teacher's reviews. Texts are plain: apps show them as text, never as markup. Apps show
 * [level] only when [confidence] is [SummaryConfidence.MEDIUM] or [SummaryConfidence.HIGH].
 *
 * Invariants checked on decode and construction: [reviewCount] is at least 3, and [scales] holds exactly five
 * scales of distinct kinds.
 *
 * @property reviewCount The number of reviews the shown summary was built from; it may lag behind the current
 *   reviews until a new summary is built.
 * @property tags Codes of Backend's fixed tag list, kept as strings so that a tag added later does not break the
 *   answer; apps skip codes they do not know.
 * @property scales The five scales in Backend's order `EXPLAINS`, `ATTITUDE`, `FAIRNESS`, `STRICTNESS`, `WORKLOAD`.
 */
@Serializable
data class TeacherSummary(
    val reviewCount: Int,
    val description: String,
    val pros: List<String>,
    val cons: List<String>,
    val tags: List<String>,
    val scales: List<TeacherSummaryScale>,
    val level: SummaryLevel,
    val confidence: SummaryConfidence,
    @Serializable(with = WireInstantSerializer::class) val generatedAt: Instant,
) {
    init {
        require(reviewCount >= MIN_SUMMARY_REVIEWS) { "A summary is built from at least $MIN_SUMMARY_REVIEWS reviews" }
        require(scales.size == SUMMARY_SCALES && scales.map { it.kind }.toSet().size == SUMMARY_SCALES) {
            "A summary has $SUMMARY_SCALES scales of distinct kinds"
        }
    }
}

private const val MIN_SUMMARY_REVIEWS = 3
private const val SUMMARY_SCALES = 5

/** One scale of a [TeacherSummary]; [reason] is `null` when [value] is [SummaryScaleValue.NOT_ENOUGH_DATA]. */
@Serializable
data class TeacherSummaryScale(
    val kind: SummaryScaleKind,
    val value: SummaryScaleValue,
    val reason: String?,
) {
    init {
        require(value != SummaryScaleValue.NOT_ENOUGH_DATA || reason == null) { "A scale without data has no reason" }
    }
}

/**
 * An item of `GET /api/teachers/summary-levels`: the tone of a shown summary whose confidence is `MEDIUM` or `HIGH`.
 * [teacherIsu] is positive; checked on decode and construction.
 */
@Serializable
data class TeacherSummaryLevel(
    val teacherIsu: Int,
    val level: SummaryLevel,
) {
    init {
        require(teacherIsu > 0) { "teacherIsu is positive" }
    }
}

/** The overall tone of the reviews behind a summary. A display enum: a newer value decodes as [UNKNOWN]. */
@Serializable(with = SummaryLevelSerializer::class)
enum class SummaryLevel { VERY_NEGATIVE, NEGATIVE, MIXED, POSITIVE, VERY_POSITIVE, UNKNOWN }

internal object SummaryLevelSerializer :
    UnknownTolerantEnumSerializer<SummaryLevel>("SummaryLevel", SummaryLevel.entries, SummaryLevel.UNKNOWN)

/**
 * How much the reviews agree; always [LOW] when fewer than five reviews went in. A display enum: a newer value
 * decodes as [UNKNOWN].
 */
@Serializable(with = SummaryConfidenceSerializer::class)
enum class SummaryConfidence { LOW, MEDIUM, HIGH, UNKNOWN }

internal object SummaryConfidenceSerializer : UnknownTolerantEnumSerializer<SummaryConfidence>(
    "SummaryConfidence",
    SummaryConfidence.entries,
    SummaryConfidence.UNKNOWN,
)

/** The five scales in display order. A display enum: a newer kind decodes as [UNKNOWN]. */
@Serializable(with = SummaryScaleKindSerializer::class)
enum class SummaryScaleKind { EXPLAINS, ATTITUDE, FAIRNESS, STRICTNESS, WORKLOAD, UNKNOWN }

internal object SummaryScaleKindSerializer : UnknownTolerantEnumSerializer<SummaryScaleKind>(
    "SummaryScaleKind",
    SummaryScaleKind.entries,
    SummaryScaleKind.UNKNOWN,
)

/** The value of one scale. A display enum: a newer value decodes as [UNKNOWN]. */
@Serializable(with = SummaryScaleValueSerializer::class)
enum class SummaryScaleValue { LOW, MEDIUM, HIGH, NOT_ENOUGH_DATA, UNKNOWN }

internal object SummaryScaleValueSerializer : UnknownTolerantEnumSerializer<SummaryScaleValue>(
    "SummaryScaleValue",
    SummaryScaleValue.entries,
    SummaryScaleValue.UNKNOWN,
)
