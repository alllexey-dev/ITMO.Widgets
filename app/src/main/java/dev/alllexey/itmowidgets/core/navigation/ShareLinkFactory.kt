package dev.alllexey.itmowidgets.core.navigation

/**
 * Builds the links «Поделиться» sends; [baseUrl] is the site of this build, so they parse back with [AppLinks].
 */
class ShareLinkFactory(private val baseUrl: String) {

    fun profile(isu: Int): String {
        require(isu > 0) { "A shared profile needs a positive ISU, got $isu" }
        return "$baseUrl${AppLinks.PROFILE_PREFIX}$isu"
    }

    fun sportLesson(lessonId: Long): String {
        require(lessonId > 0) { "A shared sport lesson needs a positive id, got $lessonId" }
        return "$baseUrl${AppLinks.SPORT_PREFIX}$lessonId"
    }

    /** A predicted lesson is named by the catalog lesson it repeats. */
    fun predictedSportLesson(prototypeLessonId: Long): String {
        require(prototypeLessonId > 0) { "A shared predicted lesson needs a positive prototype id, got $prototypeLessonId" }
        return "$baseUrl${AppLinks.PREDICTED_SPORT_PREFIX}$prototypeLessonId"
    }
}
