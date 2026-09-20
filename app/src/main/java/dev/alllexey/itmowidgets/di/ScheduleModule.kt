package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.app.WidgetRefreshCoordinator
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.schedule.data.LessonFriendsRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.SubjectLessonsGatewayImpl
import dev.alllexey.itmowidgets.feature.schedule.data.home.ScheduleHomeCardSource
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSourceImpl
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSource
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSourceImpl
import dev.alllexey.itmowidgets.feature.schedule.data.repository.ScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetSnapshotStoreImpl
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleModule {

    @Binds
    abstract fun bindScheduleWidgetRefreshRequester(
        impl: WidgetRefreshCoordinator
    ): ScheduleWidgetRefreshRequester

    @Binds
    @Singleton
    abstract fun bindScheduleLocalDataSource(
        impl: ScheduleLocalDataSourceImpl
    ): ScheduleLocalDataSource

    @Binds
    @Singleton
    abstract fun bindScheduleRemoteDataSource(
        impl: ScheduleRemoteDataSourceImpl
    ): ScheduleRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(
        impl: ScheduleRepositoryImpl
    ): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindLessonFriendsRepository(
        impl: LessonFriendsRepositoryImpl
    ): LessonFriendsRepository

    @Binds
    @Singleton
    abstract fun bindSubjectLessonsGateway(
        impl: SubjectLessonsGatewayImpl
    ): SubjectLessonsGateway

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindScheduleSessionDataCleaner(
        impl: ScheduleRepositoryImpl
    ): SessionDataCleaner

    @Binds
    @Singleton
    abstract fun bindScheduleRefreshGateway(
        impl: ScheduleRepositoryImpl
    ): ScheduleRefreshGateway

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindScheduleHomeCards(
        impl: ScheduleHomeCardSource
    ): HomeCardSource

    @Binds
    @Singleton
    abstract fun bindScheduleWidgetSnapshotStore(
        impl: ScheduleWidgetSnapshotStoreImpl
    ): ScheduleWidgetSnapshotStore

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindScheduleWidgetSnapshotCleaner(
        impl: ScheduleWidgetSnapshotStoreImpl
    ): SessionDataCleaner
}
