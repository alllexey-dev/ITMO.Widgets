package dev.alllexey.itmowidgets.feature.social.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class SocialModuleTest {

    /** The data resolves inside the module; only the platform's core types and the teacher reviews are bridged. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theSocialModuleResolvesWithTheBridgedTypes() {
        socialModule.verify(
            extraTypes = listOf(
                BackendGate::class,
                UsersApi::class,
                FriendsApi::class,
                MyItmoClient::class,
                CoroutineScope::class,
                DemoMode::class,
                AppDispatchers::class,
                CustomServicesRepository::class,
                TeacherReviewsRepository::class,
                CurrentUserProvider::class,
                SavedStateHandle::class,
            )
        )
    }
}
