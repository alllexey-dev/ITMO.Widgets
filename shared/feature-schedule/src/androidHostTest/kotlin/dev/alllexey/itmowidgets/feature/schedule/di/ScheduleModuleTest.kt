package dev.alllexey.itmowidgets.feature.schedule.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler
import kotlin.test.Test
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

class ScheduleModuleTest {

    /**
     * The schedule data, the calendar sync, the selectors and the three screens resolve inside the two modules; the
     * core contracts, the notifier, the phone's calendars and the schedulers, which the platform supplies (the app's
     * bridges on Android), are given.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theScheduleModulesResolveWithThePlatformTypes() {
        module { includes(scheduleModule, scheduleDataModule) }.verify(
            extraTypes = listOf(
                AcademicTimeProvider::class,
                Clock::class,
                AppDispatchers::class,
                AppDirectories::class,
                DemoMode::class,
                BackendGate::class,
                MyItmoClient::class,
                ScheduleApi::class,
                AppNotifier::class,
                ScheduleChangeNotifier::class,
                ScheduleChangesScheduler::class,
                PhoneCalendars::class,
                CalendarSyncScheduler::class,
                BuildingDirectory::class,
                SessionTokenStore::class,
                ScheduleCheckPreferences::class,
                WidgetSettingsPreferences::class,
                SchedulePreferencesRepository::class,
                PendingSportBookingsRepository::class,
                CustomServicesRepository::class,
                TeacherLevelsRepository::class,
                SavedStateHandle::class,
            ),
        )
    }
}
