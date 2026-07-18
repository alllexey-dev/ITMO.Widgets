package dev.alllexey.itmowidgets.domain.model.sport

import api.myitmo.model.sport.SportLesson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Type
import java.time.OffsetDateTime

sealed class UnavailableReason(val shortDescription: String, val weight: Int) {
    object Full : UnavailableReason("Нет мест", 10)
    object AlreadyEnrolled : UnavailableReason("Вы уже записаны", 20)
    object TimeConflict : UnavailableReason("Есть запись в это время", 30)
    object DailyLimitReached : UnavailableReason("Лимит записей на день", 40)
    object WeeklyLimitReached : UnavailableReason("Лимит записей на неделе", 50)
    object CreditAchieved : UnavailableReason("Зачёт достигнут", 55)
    object SelectionFailed : UnavailableReason("Не пройден отбор", 60)
    object ExternatOnly : UnavailableReason("Занятие для экстерната", 65)
    object HealthGroupMismatch : UnavailableReason("Другая группа здоровья", 70)
    object LessonInPast : UnavailableReason("Занятие в прошлом", 90)
    class Other(reason: String) : UnavailableReason(reason, 100)

    companion object {
        fun getSortedUnavailableReasons(
            lesson: SportLesson,
            now: OffsetDateTime
        ): List<UnavailableReason> {
            val reasons = mutableSetOf<UnavailableReason>()

            if (lesson.signed == true) {
                reasons.add(AlreadyEnrolled)
            }

            if (lesson.date.isBefore(now)) {
                reasons.add(LessonInPast)
            }

            if ((lesson.available ?: 1) <= 0 && lesson.signed != true) {
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
                reasonString.startsWith("Занятие для экстерната") -> ExternatOnly
                reasonString.startsWith("Вы уже записаны") -> AlreadyEnrolled
                reasonString.startsWith("Набрано необходимое количество баллов") -> CreditAchieved
                else -> Other(reasonString)
            }
        }
    }
}

class UnavailableReasonTypeAdapter : JsonDeserializer<UnavailableReason> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): UnavailableReason {
        val jsonObject = json.asJsonObject
        return when (val reasonString = jsonObject.get("shortDescription")?.asString) {
            "Нет мест" -> UnavailableReason.Full
            "Вы уже записаны" -> UnavailableReason.AlreadyEnrolled
            "Есть запись в это время" -> UnavailableReason.TimeConflict
            "Лимит записей на день" -> UnavailableReason.DailyLimitReached
            "Лимит записей на неделе" -> UnavailableReason.WeeklyLimitReached
            "Зачёт достигнут" -> UnavailableReason.CreditAchieved
            "Не пройден отбор" -> UnavailableReason.SelectionFailed
            "Занятие для экстерната" -> UnavailableReason.ExternatOnly
            "Другая группа здоровья" -> UnavailableReason.HealthGroupMismatch
            "Занятие в прошлом" -> UnavailableReason.LessonInPast
            else -> {
                val otherReason = jsonObject.get("reason")?.asString ?: reasonString ?: "Неизвестная причина"
                UnavailableReason.Other(otherReason)
            }
        }
    }
}
