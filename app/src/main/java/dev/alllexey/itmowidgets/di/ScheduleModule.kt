package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.app.WidgetRefreshCoordinator
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.schedule.data.LessonFriendsRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.SubjectLessonsGatewayImpl
import dev.alllexey.itmowidgets.feature.schedule.data.TeacherLessonsGatewayImpl
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.AndroidPhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.CalendarSyncRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.DefaultCalendarSync
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.MyItmoOwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.data.changes.DefaultScheduleChangeTracking
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.home.ScheduleChangesHomeCardSource
import dev.alllexey.itmowidgets.feature.schedule.data.home.ScheduleHomeCardSource
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSourceImpl
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSource
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSourceImpl
import dev.alllexey.itmowidgets.feature.schedule.data.repository.ScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetSnapshotStoreImpl
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import dev.alllexey.itmowidgets.feature.schedule.work.AndroidScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.work.WorkManagerCalendarSyncScheduler
import dev.alllexey.itmowidgets.feature.schedule.work.WorkManagerScheduleChangesScheduler
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
    @Singleton
    abstract fun bindTeacherLessonsGateway(
        impl: TeacherLessonsGatewayImpl
    ): TeacherLessonsGateway

    @Binds
    @IntoSet
    abstract fun bindTeacherLessonsSessionDataCleaner(
        impl: TeacherLessonsGatewayImpl
    ): SessionDataCleaner

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
    @IntoSet
    @Singleton
    abstract fun bindScheduleChangesHomeCards(
        impl: ScheduleChangesHomeCardSource
    ): HomeCardSource

    @Binds
    @Singleton
    abstract fun bindScheduleChangesRepository(
        impl: ScheduleChangesRepositoryImpl
    ): ScheduleChangesRepository

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindScheduleChangesSessionDataCleaner(
        impl: ScheduleChangesRepositoryImpl
    ): SessionDataCleaner

    @Binds
    abstract fun bindScheduleChangesScheduler(
        impl: WorkManagerScheduleChangesScheduler
    ): ScheduleChangesScheduler

    @Binds
    abstract fun bindScheduleChangeNotifier(
        impl: AndroidScheduleChangeNotifier
    ): ScheduleChangeNotifier

    @Binds
    @Singleton
    abstract fun bindScheduleChangeTracking(
        impl: DefaultScheduleChangeTracking
    ): ScheduleChangeTracking

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

    @Binds
    abstract fun bindPhoneCalendars(
        impl: AndroidPhoneCalendars
    ): PhoneCalendars

    @Binds
    abstract fun bindOwnScheduleSource(
        impl: MyItmoOwnScheduleSource
    ): OwnScheduleSource

    @Binds
    @Singleton
    abstract fun bindCalendarSyncRepository(
        impl: CalendarSyncRepositoryImpl
    ): CalendarSyncRepository

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindCalendarSyncSessionDataCleaner(
        impl: CalendarSyncRepositoryImpl
    ): SessionDataCleaner

    @Binds
    abstract fun bindCalendarSyncScheduler(
        impl: WorkManagerCalendarSyncScheduler
    ): CalendarSyncScheduler

    @Binds
    @Singleton
    abstract fun bindCalendarSync(
        impl: DefaultCalendarSync
    ): CalendarSync
}
