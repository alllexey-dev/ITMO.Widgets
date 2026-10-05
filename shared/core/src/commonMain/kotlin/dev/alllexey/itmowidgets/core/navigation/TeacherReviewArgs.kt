package dev.alllexey.itmowidgets.core.navigation

import kotlinx.serialization.Serializable

/** The teacher a review editor or a report dialog is about; [teacherName] is the profile's full name. */
@Serializable
data class TeacherReviewArgs(val teacherIsu: Int, val teacherName: String) {
    companion object {
        const val TEACHER_ISU = "review_teacher_isu"
        const val TEACHER_NAME = "review_teacher_name"
        /** The review a report is about. */
        const val REVIEW_ID = "review_id"
    }
}
