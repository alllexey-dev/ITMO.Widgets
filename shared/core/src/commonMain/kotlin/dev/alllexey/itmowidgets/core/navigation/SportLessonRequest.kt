package dev.alllexey.itmowidgets.core.navigation

/**
 * A request to show one sport lesson from a shared link on the sign page.
 *
 * Set as a Fragment result on the Activity's FragmentManager, which keeps it, also in the saved state, until a
 * listener is `STARTED` and delivers it once. Only the root sport screen listens and hands it to its sign page.
 * [LESSON_ID] is the lesson's catalog id; with [PREDICTED] it is the prototype the predicted lesson repeats.
 */
object SportLessonRequest {
    const val KEY = "sport_lesson_request"
    const val LESSON_ID = "lesson_id"
    const val PREDICTED = "predicted"
}
