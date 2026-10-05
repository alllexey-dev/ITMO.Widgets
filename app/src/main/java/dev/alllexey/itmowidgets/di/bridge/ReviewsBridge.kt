package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the teacher reviews and levels repositories. Hilt constructs each `@Singleton` that its
 * `SessionDataCleaner` contribution also holds, so the cleaners and Koin readers share it (one graph per binding).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReviewsBridgeEntryPoint {
    fun teacherReviewsRepository(): TeacherReviewsRepository
    fun teacherLevelsRepository(): TeacherLevelsRepository

    companion object {
        fun from(context: Context): ReviewsBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ReviewsBridgeEntryPoint::class.java)
    }
}

/** Lazy singles: Koin starts before Hilt builds its component, so each one reads Hilt on first use. */
val reviewsBridgeModule = module {
    single<TeacherReviewsRepository> { ReviewsBridgeEntryPoint.from(androidContext()).teacherReviewsRepository() }
    single<TeacherLevelsRepository> { ReviewsBridgeEntryPoint.from(androidContext()).teacherLevelsRepository() }
}
