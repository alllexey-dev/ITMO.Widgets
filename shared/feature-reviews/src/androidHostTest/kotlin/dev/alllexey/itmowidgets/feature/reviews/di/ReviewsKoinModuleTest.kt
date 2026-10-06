package dev.alllexey.itmowidgets.feature.reviews.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class ReviewsKoinModuleTest {

    /** The repository and the lessons gateway are bridged from the app's Hilt graph, the handle comes from the host. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theReviewsModuleResolvesWithTheBridgedTypes() {
        reviewsModule.verify(
            extraTypes = listOf(TeacherReviewsRepository::class, TeacherLessonsGateway::class, SavedStateHandle::class),
        )
    }
}
