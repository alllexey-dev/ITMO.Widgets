package dev.alllexey.itmowidgets.feature.sport.domain.model

import java.time.OffsetDateTime

sealed class UnavailableReason(val weight: Int) {
    object Full : UnavailableReason(10)
    object AlreadyEnrolled : UnavailableReason(20)
    object TimeConflict : UnavailableReason(30)
    object DailyLimitReached : UnavailableReason(40)
    object WeeklyLimitReached : UnavailableReason(50)
    object CreditAchieved : UnavailableReason(55)
    object SelectionFailed : UnavailableReason(60)
    object ExternatOnly : UnavailableReason(65)
    object DebtOnly : UnavailableReason(67)
    object HealthGroupMismatch : UnavailableReason(70)
    object LessonInPast : UnavailableReason(90)
    data class Other(val reason: String) : UnavailableReason(100)

    companion object {
        fun getSortedUnavailableReasons(
            signed: Boolean,
            startsAt: OffsetDateTime,
            available: Int,
            serverReasons: List<String>,
            now: OffsetDateTime
        ): List<UnavailableReason> {
            val reasons = mutableSetOf<UnavailableReason>()

            if (signed) {
                reasons.add(AlreadyEnrolled)
            }

            if (startsAt.isBefore(now)) {
                reasons.add(LessonInPast)
            }

            if (available <= 0 && !signed) {
                reasons.add(Full)
            }

            serverReasons.mapTo(reasons, ::parseReasonFromString)
            return reasons.sortedBy { it.weight }.distinct()
        }

        private fun parseReasonFromString(rawReason: String): UnavailableReason {
            val reasonString = rawReason.trim()
            return when {
                reasonString.startsWith("Занятие в прошлом") -> LessonInPast
                reasonString.startsWith("Не пройден отбор") -> SelectionFailed
                reasonString.startsWith("Выбрано 2 занятия на неделе") -> WeeklyLimitReached
                reasonString.startsWith("Выбрано 1 занятие в этот день") -> DailyLimitReached
                reasonString.startsWith("Нет необходимой группы здоровья") -> HealthGroupMismatch
                reasonString.startsWith("Есть запись на занятия в это время") -> TimeConflict
                reasonString.startsWith("Занятие для экстерната") -> ExternatOnly
                reasonString.startsWith("Занятие для студентов с задолженностью") -> DebtOnly
                reasonString.startsWith("Вы уже записаны") -> AlreadyEnrolled
                reasonString.startsWith("Набрано необходимое количество баллов") -> CreditAchieved
                else -> Other(reasonString)
            }
        }
    }
}
