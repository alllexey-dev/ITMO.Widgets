package dev.alllexey.itmowidgets.feature.social.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.social.data.UnofferedTeacherReviews
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchViewModel
import dev.alllexey.itmowidgets.feature.social.ui.home.FriendRequestsHomeCardRenderer
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * The social routes' graph as the iOS app starts it (`IosKoinModules`): the core module, [socialModule] and
 * [socialIosModule], with the account types, the services opt-in (`settingsDataModule` on iOS) and the simulator test
 * binary's device stood in, on the demo session: the screens resolve and answer from the demo set with no request,
 * and the profile reads no teacher reviews while iOS does not offer them.
 */
class SocialIosModuleTest {

    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-social-test-${Random.nextLong().toULong()}"
    private val requests = mutableListOf<String>()

    init {
        FileSystem.SYSTEM.createDirectories(root)
    }

    @AfterTest
    fun deleteDevice() = FileSystem.SYSTEM.deleteRecursively(root)

    /**
     * Koin's `verify()` is JVM-only; on Kotlin/Native every definition is resolved instead, each with the arguments
     * the Swift host gives the profile and a user's friends (unused by the others).
     */
    @OptIn(KoinInternalApi::class)
    @Test
    fun everyDefinitionOfTheSocialRoutesResolvesWithoutARequest() {
        val koin = graph()
        val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()

        definitions.forEach { definition ->
            koin.get<Any>(definition.primaryType, definition.qualifier) { parametersOf(arguments()) }
        }

        koin.get<FriendsViewModel>()
        koin.get<UserSearchViewModel>()
        koin.get<UserProfileViewModel> { parametersOf(arguments()) }
        koin.get<UserFriendsViewModel> { parametersOf(arguments()) }
        assertSame<TeacherReviewsRepository>(UnofferedTeacherReviews, koin.get())
        assertEquals(listOf<HomeCardRenderer>(FriendRequestsHomeCardRenderer), koin.getAll<HomeCardRenderer>())
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun theDemoSearchAndProfileAnswerWithoutARequest() = runTest {
        val koin = graph()

        val page = koin.get<PeopleSearchRepository>().search("Софи")
        val profile = koin.get<SocialRepository>().profile(DemoPeople.SOFIA.isu)
        val request = koin.get<SocialRepository>().acceptRequest(DemoPeople.SOFIA.isu)

        val found = assertIs<AppResult.Success<PeopleSearchPage>>(page).value.results
        assertEquals(listOf(DemoPeople.SOFIA.isu), found.map { it.isu })
        assertEquals(RelationshipState.INCOMING, assertIs<AppResult.Success<UserProfile>>(profile).value.relationship)
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), request)
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun theUnofferedReviewsAnswerNothingAndReportNoFailure() = runTest {
        val reviews = UnofferedTeacherReviews

        val teacher = DemoPeople.MATH_TEACHER.isu
        val unoffered = AppResult.Failure(AppError.CustomServicesDisabled)

        assertNull(reviews.cachedReviews(teacher))
        assertEquals(unoffered, reviews.reviews(teacher))
        assertEquals(unoffered, reviews.vote(teacher, "r1", 1))
        assertEquals(emptyList(), reviews.observeUpdates().toList())
    }

    private fun arguments() = SavedStateHandle(
        mapOf(UserScreenArgs.ISU to DemoPeople.IVAN.isu, UserScreenArgs.NAME to DemoPeople.IVAN.name),
    )

    private fun graph(): Koin = koinApplication {
        allowOverride(true)
        modules(
            iosCoreModule(FakeHost(), ORIGIN), socialModule, socialIosModule(INVITE_URL), platformTypes(), testDevice(),
        )
    }.koin

    private fun platformTypes(): Module = module {
        single<DemoMode> { FakeDemoMode(active = true) }
        single<CurrentUserProvider> {
            object : CurrentUserProvider {
                override suspend fun getCurrentUser(): CurrentUser = DemoPeople.ME
            }
        }
        single<CustomServicesRepository> { FakeCustomServicesRepository(enabled = true) }
    }

    /** A counting engine, directories and the App Group under the test's temporary directory, no main queue. */
    private fun testDevice(): Module = module {
        single<HttpClientEngine> {
            MockEngine { request ->
                requests += request.url.toString()
                respondError(HttpStatusCode.ServiceUnavailable)
            }
        }
        single<AppDirectories> {
            object : AppDirectories {
                override val files = root / "files"
                override val cache = root / "caches"
                override val noBackup = root / "no-backup"
            }
        }
        single<AppGroupDirectory> {
            AppGroupDirectory.resolve(
                identifiers = BundleIdentifiers(appGroupId = "group.test.itmo", keychainGroup = null),
                appDirectories = get(),
                log = get<AppLog>(),
                containerOf = { root / "group" },
            )
        }
        single<AppDispatchers> { AppDispatchers(Dispatchers.Default, Dispatchers.Default, Dispatchers.Default) }
    }

    private class FakeHost : IosCoreHost {
        override fun topViewController(): UIViewController? = null

        override fun clearWebsiteData(completion: () -> Unit) = completion()

        override fun reload(kind: String) = Unit
    }

    private companion object {
        const val ORIGIN = "https://dev.widgets.alllexey.dev"
        const val INVITE_URL = ORIGIN
    }
}
