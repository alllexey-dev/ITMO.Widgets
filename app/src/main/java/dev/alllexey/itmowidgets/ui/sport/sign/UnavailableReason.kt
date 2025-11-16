package dev.alllexey.itmowidgets.ui.sport.sign

import api.myitmo.model.sport.SportLesson
import java.time.OffsetDateTime

sealed class UnavailableReason(val shortDescription: String, val weight: Int) {
    object Full : UnavailableReason("Нет мест", 10)
    object AlreadyEnrolled : UnavailableReason("Вы уже записаны", 20)
    object TimeConflict : UnavailableReason("Есть запись в это время", 30)
    object DailyLimitReached : UnavailableReason("Лимит записей на день", 40)
    object WeeklyLimitReached : UnavailableReason("Лимит записей на неделе", 50)
    object SelectionFailed : UnavailableReason("Не пройден отбор", 60)
    object HealthGroupMismatch : UnavailableReason("Другая группа здоровья", 70)
    object LessonInPast : UnavailableReason("Занятие в прошлом", 80)
    class Other(reason: String) : UnavailableReason(reason, 100)

    companion object {
        fun getSortedUnavailableReasons(lesson: SportLesson): List<UnavailableReason> {
            val reasons = mutableSetOf<UnavailableReason>()

            if (lesson.signed == true) {
                reasons.add(AlreadyEnrolled)
            }

            if (lesson.date.isBefore(OffsetDateTime.now())) {
                reasons.add(LessonInPast)
            }

            if (lesson.available != null && lesson.available <= 0 && lesson.signed != true) {
                reasons.add(Full)
            }

            lesson.canSignIn?.unavailableReasons?.mapTo(reasons) { parseReasonFromString(it) }
            return reasons.sortedBy { it.weight }.distinct()
        }

        private fun parseReasonFromString(reasonString: String): UnavailableReason {
            return when {
                reasonString.startsWith("Занятие в прошлом") -> LessonInPast
                reasonString.startsWith("Не пройден отбор") -> SelectionFailed
                reasonString.startsWith("Выбрано 2 занятия на неделе") -> WeeklyLimitReached
                reasonString.startsWith("Выбрано 1 занятие в этот день") -> DailyLimitReached
                reasonString.startsWith("Нет необходимой группы здоровья") -> HealthGroupMismatch
                reasonString.startsWith("Есть запись на занятия в это время") -> TimeConflict
                reasonString.startsWith("Вы уже записаны") -> AlreadyEnrolled
                else -> Other(reasonString)
            }
        }
    }
}
