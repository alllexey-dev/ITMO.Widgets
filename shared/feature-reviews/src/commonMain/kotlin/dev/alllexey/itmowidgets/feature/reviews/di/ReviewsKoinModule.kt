package dev.alllexey.itmowidgets.feature.reviews.di

import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewViewModel
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The review editor's and the report dialog's ViewModels. `TeacherReviewsRepository` comes from the app's
 * `ReviewsBridge` and `TeacherLessonsGateway` from the core bridge until the data moves to Koin; the
 * `SavedStateHandle` carries the host's `TeacherReviewArgs`.
 */
val reviewsModule = module {
    viewModelOf(::ReviewEditorViewModel)
    viewModelOf(::ReportReviewViewModel)
}
