package dev.alllexey.itmowidgets.feature.reviews.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs

/**
 * The Koin parameters of the report sheet's `ReportReviewViewModel` (`AppRoutes.ReportReview`): a SavedStateHandle with
 * what Android's shell seeds from the route, since a SwiftUI store has no saved-state registry.
 */
fun reportReviewParameters(args: TeacherReviewArgs, reviewId: String): List<Any> = listOf(
    SavedStateHandle(
        mapOf(
            TeacherReviewArgs.TEACHER_ISU to args.teacherIsu,
            TeacherReviewArgs.TEACHER_NAME to args.teacherName,
            TeacherReviewArgs.REVIEW_ID to reviewId,
        ),
    ),
)
