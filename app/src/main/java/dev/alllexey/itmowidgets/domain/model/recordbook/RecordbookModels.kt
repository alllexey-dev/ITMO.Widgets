package dev.alllexey.itmowidgets.domain.model.recordbook

import java.time.OffsetDateTime

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
    val semesterInCourse: Int get() = if (semester % 2 == 0) 2 else 1
}

enum class RecordbookSubjectStatus {
    PASSED,
    ATTENTION,
    IN_PROGRESS
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
    val teacherName: String?
) {
    val status: RecordbookSubjectStatus
        get() = when {
            rate.isNullOrBlank() -> RecordbookSubjectStatus.IN_PROGRESS
            rate.trim().startsWith("2") -> RecordbookSubjectStatus.ATTENTION
            rate.contains("не зач", ignoreCase = true) -> RecordbookSubjectStatus.ATTENTION
            else -> RecordbookSubjectStatus.PASSED
        }

    val displayRate: String
        get() {
            val normalizedRate = rate?.trim()
            return when {
                normalizedRate.equals("зачет", ignoreCase = true) ||
                    normalizedRate.equals("зачёт", ignoreCase = true) -> "Зачёт"
                normalizedRate.isNullOrBlank() -> "В процессе"
                else -> normalizedRate.replace("/", "").replace(" ", "").uppercase()
            }
        }
}

data class RecordbookControl(
    val id: Long,
    val name: String,
    val score: Double?,
    val minimum: Double,
    val maximum: Double,
    val required: Boolean,
    val date: OffsetDateTime?,
    val teacherName: String?
)
