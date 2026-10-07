package dev.alllexey.itmowidgets.feature.reviews.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import kotlin.test.Test
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class ReviewsKoinModuleTest {

    /** The data resolves inside the module; only the platform's core types are bridged, the handle is the host's. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theReviewsModuleResolvesWithTheBridgedTypes() {
        reviewsModule.verify(
            extraTypes = listOf(
                BackendGate::class,
                TeacherReviewsApi::class,
                AppDirectories::class,
                Clock::class,
                AcademicTimeProvider::class,
                CoroutineScope::class,
                DemoMode::class,
                AppDispatchers::class,
                TeacherLessonsGateway::class,
                SavedStateHandle::class,
            )
        )
    }
}
