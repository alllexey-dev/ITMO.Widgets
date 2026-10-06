package dev.alllexey.itmowidgets.feature.social.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class SocialModuleTest {

    /** The repositories and the current user are bridged from the app's Hilt graph. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theSocialModuleResolvesWithTheBridgedTypes() {
        socialModule.verify(
            extraTypes = listOf(
                SocialRepository::class,
                PeopleSearchRepository::class,
                PersonRepository::class,
                TeacherReviewsRepository::class,
                CurrentUserProvider::class,
                SavedStateHandle::class,
            )
        )
    }
}
