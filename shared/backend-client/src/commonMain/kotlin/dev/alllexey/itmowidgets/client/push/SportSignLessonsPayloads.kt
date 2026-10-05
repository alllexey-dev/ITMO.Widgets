package dev.alllexey.itmowidgets.client.push

import dev.alllexey.itmowidgets.client.sport.model.SportLessonDto
import kotlinx.serialization.Serializable

/**
 * `SPORT_FREE_SIGN_LESSONS_PAYLOAD`: a free-queue attempt was reserved for these lessons with a free place; the app
 * books them. The message expires at the entry's eligibility deadline.
 */
@Serializable
data class SportFreeSignLessonsPayload(
    val sportLessons: List<SportLessonDto>,
) {
    companion object {
        const val TYPE: String = "SPORT_FREE_SIGN_LESSONS_PAYLOAD"
    }
}

/**
 * `SPORT_AUTO_SIGN_LESSONS_PAYLOAD`: an auto-queue attempt was reserved for these matched real lessons; the app
 * books them. The message expires at the end of the matched lesson.
 */
@Serializable
data class SportAutoSignLessonsPayload(
    val sportLessons: List<SportLessonDto>,
) {
    companion object {
        const val TYPE: String = "SPORT_AUTO_SIGN_LESSONS_PAYLOAD"
    }
}
