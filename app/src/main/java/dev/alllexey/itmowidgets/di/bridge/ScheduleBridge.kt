package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.home.HomeScheduleSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the schedule data the screens read. Hilt constructs each `@Singleton` until the data moves
 * (KM-11a); the widget, the workers, the home cards and the session cleaners share it with Koin's readers.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduleBridgeEntryPoint {
    fun scheduleRepository(): ScheduleRepository
    fun scheduleChangesRepository(): ScheduleChangesRepository
    fun lessonFriendsRepository(): LessonFriendsRepository

    companion object {
        fun from(context: Context): ScheduleBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ScheduleBridgeEntryPoint::class.java)
    }
}

/** Lazy singles: Koin starts before Hilt builds its component, so each one reads Hilt on first use. */
val scheduleBridgeModule = module {
    single<ScheduleRepository> { ScheduleBridgeEntryPoint.from(androidContext()).scheduleRepository() }
    single<ScheduleChangesRepository> { ScheduleBridgeEntryPoint.from(androidContext()).scheduleChangesRepository() }
    single<LessonFriendsRepository> { ScheduleBridgeEntryPoint.from(androidContext()).lessonFriendsRepository() }
}

/**
 * Koin to Hilt for the selectors `scheduleModule` constructs: `ScheduleWidgetDataProvider`, `ScheduleHomeCardSource`,
 * `ScheduleSettingsPreview` and `DefaultWidgetPreviewFactory` still take them from Hilt. Unscoped: Koin owns the
 * lifetime. `ensureStarted`, because a widget broadcast or a worker can run before `Application.onCreate()`.
 */
@Module
@InstallIn(SingletonComponent::class)
object ScheduleKoinBridge {

    @Provides
    fun scheduleWidgetSelector(@ApplicationContext context: Context): ScheduleWidgetSelector =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun schedulePreviewScenario(@ApplicationContext context: Context): SchedulePreviewScenario =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun homeScheduleSelector(@ApplicationContext context: Context): HomeScheduleSelector =
        KoinStarter.ensureStarted(context).get()
}
