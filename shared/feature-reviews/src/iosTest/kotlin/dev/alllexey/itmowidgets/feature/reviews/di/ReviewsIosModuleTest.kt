package dev.alllexey.itmowidgets.feature.reviews.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLessonsGateway
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewViewModel
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorViewModel
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
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
 * The teacher reviews' graph as the iOS app starts it (`IosKoinModules`): the core module, [reviewsModule] and
 * [reviewsIosModule], with the account types, the services opt-in (`settingsDataModule` on iOS), the teacher's lessons
 * (`scheduleDataModule`) and the simulator test binary's device stood in, on the demo session: the editor and the
 * report resolve with the arguments the Swift hosts give them, a demo teacher's reviews and tone come from
 * `DemoReviews` with no request, and every change is refused in the demo without one.
 */
class ReviewsIosModuleTest {

    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-reviews-test-${Random.nextLong().toULong()}"
    private val requests = mutableListOf<String>()

    init {
        FileSystem.SYSTEM.createDirectories(root)
    }

    @AfterTest
    fun deleteDevice() = FileSystem.SYSTEM.deleteRecursively(root)

    /** Koin's `verify()` is JVM-only; on Kotlin/Native every definition is resolved instead. */
    @OptIn(KoinInternalApi::class)
    @Test
    fun everyDefinitionOfTheEditorAndTheReportResolvesWithoutARequest() {
        val koin = graph()
        val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()

        definitions.forEach { definition ->
            koin.get<Any>(definition.primaryType, definition.qualifier) { parametersOf(arguments()) }
        }

        val editor = koin.get<ReviewEditorViewModel> { parametersOf(arguments()) }
        koin.get<ReportReviewViewModel> { parametersOf(arguments()) }
        assertEquals(TEACHER.name, editor.teacherName)
        assertSame(koin.get<BackendClient>().reviews, koin.get<TeacherReviewsApi>())
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun theDemoReviewsAnswerAndEveryChangeIsRefusedWithoutARequest() = runTest {
        val koin = graph()
        val reviews = koin.get<TeacherReviewsRepository>()
        val refused = AppResult.Failure(AppError.DemoUnavailable)

        val teacher = assertIs<AppResult.Success<TeacherReviews>>(reviews.reviews(TEACHER.isu)).value
        assertTrue(teacher.reviews.isNotEmpty())
        assertTrue(teacher.canWrite && teacher.canReport)
        val review = teacher.reviews.first().id
        val draft = TeacherReviewDraft(subject = null, text = REVIEW_TEXT, anonymous = true, flowIds = emptySet())
        assertEquals(refused, reviews.save(TEACHER.isu, draft))
        assertEquals(refused, reviews.vote(TEACHER.isu, review, 1))
        assertEquals(refused, reviews.report(TEACHER.isu, review, ReviewReportReason.SPAM, null))
        assertTrue(koin.get<TeacherLevelsRepository>().levels(setOf(TEACHER.isu)).isNotEmpty(), "the demo tone")
        assertEquals(emptyList(), requests)
        koin.close()
    }

    private fun arguments() = SavedStateHandle(
        mapOf(
            TeacherReviewArgs.TEACHER_ISU to TEACHER.isu,
            TeacherReviewArgs.TEACHER_NAME to TEACHER.name,
            TeacherReviewArgs.REVIEW_ID to "review",
        ),
    )

    private fun graph(): Koin = koinApplication {
        allowOverride(true)
        modules(iosCoreModule(FakeHost(), ORIGIN), reviewsModule, reviewsIosModule, platformTypes(), testDevice())
    }.koin

    private fun platformTypes(): Module = module {
        single<DemoMode> { FakeDemoMode(active = true) }
        single<CurrentUserProvider> {
            object : CurrentUserProvider {
                override suspend fun getCurrentUser(): CurrentUser = DemoPeople.ME
            }
        }
        single<CustomServicesRepository> { FakeCustomServicesRepository(enabled = true) }
        single<TeacherLessonsGateway> { FakeTeacherLessonsGateway() }
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
        /** A demo teacher with reviews and a confident summary (`DemoReviews`). */
        val TEACHER = DemoPeople.MATH_TEACHER
        /** Long enough for the editor's minimum, so only the demo refuses it. */
        const val REVIEW_TEXT = "Лекции понятные, практики разбирают каждую задачу до конца."
    }
}
