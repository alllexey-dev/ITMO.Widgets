package dev.alllexey.itmowidgets.feature.recordbook.domain.model

import java.time.OffsetDateTime

/** BARS has its own year/season and plan identity, unrelated to MyITMO est_id. */
data class BarsJournalReference(val planId: Long, val type: String, val identifier: String,
    val yearStart: Int, val semester: Int)

data class RecordbookProgram(
    val id: Long,
    val name: String,
    val periods: List<RecordbookPeriod>
)

data class RecordbookPeriod(
    val studyYear: String,
    val semester: Int,
    val course: Int,
    val actual: Boolean
) {
    /** Seasonal half for academic date/sport matching only; UI displays the full [semester]. */
    val semesterInCourse: Int get() = if (semester % 2 == 0) 2 else 1
}

enum class RecordbookSubjectStatus {
    PASSED,
    ATTENTION,
    IN_PROGRESS
}

enum class RecordbookAssessmentKind {
    EXAM,
    CREDIT,
    OTHER
}

sealed interface RecordbookRate {
    data object Credit : RecordbookRate
    data object InProgress : RecordbookRate
    data class Grade(val code: String) : RecordbookRate
}

data class RecordbookSubject(
    val name: String,
    val disciplineId: Long,
    val entryId: Long,
    val controlType: String,
    val score: Double?,
    val rate: String?,
    val attempt: Int?,
    val examDate: OffsetDateTime?,
    val hasDetails: Boolean,
    val teacherName: String?,
    val barsJournal: BarsJournalReference? = null,
    val absent: Boolean = false,
    /** MyITMO LMS link; usually empty. */
    val lmsLink: String? = null
) {
    val assessmentKind: RecordbookAssessmentKind
        get() = when {
            controlType.contains("экзамен", ignoreCase = true) ->
                RecordbookAssessmentKind.EXAM
            controlType.contains("зач", ignoreCase = true) ->
                RecordbookAssessmentKind.CREDIT
            else -> RecordbookAssessmentKind.OTHER
        }

    private val normalizedText: String
        get() = rate.orEmpty().trim().lowercase().replace('ё', 'е')
            .replace(Regex("\\s+"), "")

    val status: RecordbookSubjectStatus
        get() = when {
            absent -> RecordbookSubjectStatus.ATTENTION
            normalizedText.startsWith("2") || normalizedText in setOf("незачет", "незачтено") ->
                RecordbookSubjectStatus.ATTENTION
            normalizedText in setOf("зачет", "зачтено") ||
                Regex("[345](/?[ABCDE])?").matches(normalizedText.uppercase()) ->
                RecordbookSubjectStatus.PASSED
            else -> RecordbookSubjectStatus.IN_PROGRESS
        }

    /** Conservative title recognition, not an identity join with schedule or other subjects. */
    val isPhysicalEducation: Boolean
        get() = name.trim().lowercase().replace('ё', 'е') in setOf(
            "физическая культура и спорт (базовая)",
            "физическая культура и спорт (элективная)"
        )

    val normalizedRate: RecordbookRate
        get() = when {
            absent -> RecordbookRate.InProgress
            normalizedText in setOf("зачет", "зачтено") -> RecordbookRate.Credit
            normalizedText.isBlank() -> RecordbookRate.InProgress
            Regex("[2345]/?[A-FX]+").matches(normalizedText.uppercase()) ->
                RecordbookRate.Grade(normalizedText.replace("/", "").uppercase())
            else -> RecordbookRate.Grade(rate.orEmpty().trim())
        }
}

data class RecordbookControl(
    val id: Long,
    val name: String,
    val score: Double?,
    val minimum: Double?,
    val maximum: Double?,
    val required: Boolean,
    val date: OffsetDateTime?,
    val teacherName: String?,
    val parentId: Long? = null,
    val absent: Boolean = false,
    val additional: Boolean = false
)
