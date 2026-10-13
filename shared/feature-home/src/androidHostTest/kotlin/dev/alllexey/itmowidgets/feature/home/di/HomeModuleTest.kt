package dev.alllexey.itmowidgets.feature.home.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.schedule.DayLessonsGateway
import dev.alllexey.itmowidgets.core.storage.BellPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.today.TodayTodoSource
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class HomeModuleTest {

    /**
     * The features' bell and todo sources, the schedule's days, the seen store and the academic time come from the
     * platform's Koin modules; the bell's repository and the three view models resolve inside the module.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theHomeModuleResolvesWithThePlatformTypes() {
        homeModule.verify(
            extraTypes = listOf(
                BellSource::class,
                TodayTodoSource::class,
                DayLessonsGateway::class,
                BellPreferences::class,
                AcademicTimeProvider::class,
                SavedStateHandle::class,
                List::class,
            )
        )
    }
}
