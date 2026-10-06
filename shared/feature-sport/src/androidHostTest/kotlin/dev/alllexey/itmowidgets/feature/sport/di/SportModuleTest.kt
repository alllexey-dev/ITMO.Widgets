package dev.alllexey.itmowidgets.feature.sport.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class SportModuleTest {

    /**
     * The controllers, the holder and the three screens resolve inside the module; the sport repositories (the app's
     * `SportBridge`) and the core contracts (its `CoreBridge`) are given.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theSportModuleResolvesWithTheBridgedTypes() {
        sportModule.verify(
            extraTypes = listOf(
                SportActionRepository::class,
                SportBookingRepository::class,
                SportDataRepository::class,
                SportScheduleRepository::class,
                SportSignPreferencesRepository::class,
                UserSportRepository::class,
                AcademicTimeProvider::class,
                CoroutineScope::class,
                ScheduleRefreshGateway::class,
                ScheduleWidgetRefreshRequester::class,
                SavedStateHandle::class,
            ),
        )
    }
}
