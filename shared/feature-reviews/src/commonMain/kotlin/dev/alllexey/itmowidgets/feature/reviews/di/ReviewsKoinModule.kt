package dev.alllexey.itmowidgets.feature.reviews.di

import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherLevelsFileStore
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherLevelsRepositoryImpl
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherReviewsRepositoryImpl
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewViewModel
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The teacher reviews and levels data, the review editor and the report dialog. Koin is the only graph for these
 * types: one repository each serves the screens, the recordbook, the schedule, the profiles and the session cleaners.
 * The core types (`BackendGate`, Core 2.0's `TeacherReviewsApi`, `AppDirectories`, the wall `Clock`, the academic
 * time, the application `CoroutineScope`, `DemoMode`, `AppDispatchers`, `TeacherLessonsGateway`) come from the
 * platform (`CoreBridge` on Android); the `SavedStateHandle` carries the host's `TeacherReviewArgs`.
 */
val reviewsModule = module {
    // Two constructors (the internal one is the tests' seam), so the public one's types are named.
    singleOf<TeacherLevelsFileStore, AppDirectories>(::TeacherLevelsFileStore)
    // One instance each; the cleaner contributions are qualified forwards (an open set).
    singleOf(::TeacherReviewsRepositoryImpl) { bind<TeacherReviewsRepository>() }
    single<SessionDataCleaner>(named("reviews")) { get<TeacherReviewsRepositoryImpl>() }
    singleOf(::TeacherLevelsRepositoryImpl) { bind<TeacherLevelsRepository>() }
    single<SessionDataCleaner>(named("teacher-levels")) { get<TeacherLevelsRepositoryImpl>() }

    viewModelOf(::ReviewEditorViewModel)
    viewModelOf(::ReportReviewViewModel)
}
