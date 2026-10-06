package dev.alllexey.itmowidgets.feature.schedule.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class ScheduleModuleTest {

    /**
     * The selectors and the three screens resolve inside the module; the schedule repositories and the core contracts
     * the app's bridges forward from Hilt are given.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theScheduleModuleResolvesWithTheBridgedTypes() {
        scheduleModule.verify(
            extraTypes = listOf(
                ScheduleRepository::class,
                ScheduleChangesRepository::class,
                LessonFriendsRepository::class,
                AcademicTimeProvider::class,
                SchedulePreferencesRepository::class,
                PendingSportBookingsRepository::class,
                CalendarSync::class,
                CustomServicesRepository::class,
                TeacherLevelsRepository::class,
                SavedStateHandle::class,
            ),
        )
    }
}
