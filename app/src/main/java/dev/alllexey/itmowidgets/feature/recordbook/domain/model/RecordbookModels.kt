package dev.alllexey.itmowidgets.feature.recordbook.domain.model

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
    val teacherName: String?
) {
    val assessmentKind: RecordbookAssessmentKind
        get() = when {
            controlType.contains("экзамен", ignoreCase = true) ->
                RecordbookAssessmentKind.EXAM
            controlType.contains("зач", ignoreCase = true) ->
                RecordbookAssessmentKind.CREDIT
            else -> RecordbookAssessmentKind.OTHER
        }

    val status: RecordbookSubjectStatus
        get() = when {
            rate.isNullOrBlank() -> RecordbookSubjectStatus.IN_PROGRESS
            rate.trim().startsWith("2") -> RecordbookSubjectStatus.ATTENTION
            rate.contains("не зач", ignoreCase = true) -> RecordbookSubjectStatus.ATTENTION
            else -> RecordbookSubjectStatus.PASSED
        }

    val normalizedRate: RecordbookRate
        get() {
            val normalizedRate = rate?.trim()
            return when {
                normalizedRate.equals("зачет", ignoreCase = true) ||
                    normalizedRate.equals("зачёт", ignoreCase = true) ->
                    RecordbookRate.Credit
                normalizedRate.isNullOrBlank() -> RecordbookRate.InProgress
                else -> RecordbookRate.Grade(
                    normalizedRate.replace("/", "").replace(" ", "").uppercase()
                )
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
) {
    val category: RecordbookControlCategory
        get() {
            val normalizedName = name.lowercase()
            return when {
                normalizedName.contains("дополнитель") ||
                    normalizedName == "зачет" ||
                    normalizedName == "зачёт" ||
                    normalizedName.contains("экзамен") -> RecordbookControlCategory.FINAL
                normalizedName.contains("homework") ||
                    normalizedName.contains("домаш") -> RecordbookControlCategory.HOMEWORK
                normalizedName.contains("test") ||
                    normalizedName.contains("practice") ||
                    normalizedName.contains("контроль") -> RecordbookControlCategory.TESTS
                else -> RecordbookControlCategory.OTHER
            }
        }
}

enum class RecordbookControlCategory {
    FINAL,
    HOMEWORK,
    TESTS,
    OTHER
}
