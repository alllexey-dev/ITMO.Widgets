package dev.alllexey.itmowidgets.feature.dormitory.di

import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import kotlin.test.Test
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class DormitoryModuleTest {

    /** Everything resolves inside the module but the platform's core types. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theDormitoryModuleResolvesWithThePlatformTypes() {
        dormitoryModule.verify(
            extraTypes = listOf(
                MyItmoClient::class,
                DemoMode::class,
                CurrentUserProvider::class,
                Clock::class,
                AcademicTimeProvider::class,
                AppDirectories::class,
                AppDispatchers::class,
                AcademicTimeProvider::class,
            ),
        )
    }
}
