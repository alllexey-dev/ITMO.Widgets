package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherLevelsRepositoryImpl
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherReviewsRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewsModule {
    @Binds
    @Singleton
    abstract fun bindTeacherReviewsRepository(impl: TeacherReviewsRepositoryImpl): TeacherReviewsRepository

    @Binds
    @IntoSet
    abstract fun bindTeacherReviewsSessionDataCleaner(impl: TeacherReviewsRepositoryImpl): SessionDataCleaner

    @Binds
    @Singleton
    abstract fun bindTeacherLevelsRepository(impl: TeacherLevelsRepositoryImpl): TeacherLevelsRepository

    @Binds
    @IntoSet
    abstract fun bindTeacherLevelsSessionDataCleaner(impl: TeacherLevelsRepositoryImpl): SessionDataCleaner
}
