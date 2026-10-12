package dev.alllexey.itmowidgets.core.navigation

/**
 * A request to show one sport lesson from a shared link on the sign page.
 * [LESSON_ID] is the lesson's catalog id; with [PREDICTED] it is the prototype the predicted lesson repeats.
 */
object SportLessonRequest {
    const val KEY = "sport_lesson_request"
    const val LESSON_ID = "lesson_id"
    const val PREDICTED = "predicted"
}
