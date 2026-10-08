package dev.alllexey.itmowidgets.feature.reviews.di

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsApi
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The iOS port of [reviewsModule], what `:app`'s `CoreBridge` gives Android: Core 2.0's reviews area from the one
 * `BackendClient`. Load it with that module; the teacher's lessons for the editor's subjects come from
 * `scheduleDataModule`, the rest (the Backend gate, the directories, the clocks, the application scope, `DemoMode`, the
 * dispatchers) from the core module (IO-09f).
 */
val reviewsIosModule: Module = module {
    single<TeacherReviewsApi> { get<BackendClient>().reviews }
}
