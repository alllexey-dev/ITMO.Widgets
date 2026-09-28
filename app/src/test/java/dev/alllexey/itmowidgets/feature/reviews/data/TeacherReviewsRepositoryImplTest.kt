package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.reviews.ExternalTeacherReview as WireReview
import dev.alllexey.itmowidgets.core.model.reviews.TeacherReviewsResponse as WireTeacherReviews
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class TeacherReviewsRepositoryImplTest {
    @Test
    fun `disabled or unknown opt-in exposes no cache and disabled requests never call Backend`() = runTest {
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(FakeServices(false), api.instance, backgroundScope)

        assertNull(repository.cachedReviews(100001))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.reviews(100001))
        runCurrent()

        assertEquals(0, api.calls)
        assertNull(repository.cachedReviews(100001))
    }

    @Test
    fun `maps exact date before year and absent date preserving Backend order`() = runTest {
        val api = FakeApi().apply {
            result = result!!.copy(external = listOf(
                review(1).copy(writtenOn = LocalDate.of(2025, 1, 25)),
                review(2).copy(writtenBeforeYear = 2024),
                review(3),
            ))
        }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope)

        val reviews = (repository.reviews(100001) as AppResult.Success).value

        assertEquals(listOf(100001), api.requestedIsus)
        assertEquals(100001, reviews.isu)
        assertEquals(listOf(UUID(0, 1).toString(), UUID(0, 2).toString(), UUID(0, 3).toString()), reviews.external.map { it.id })
        assertEquals(listOf(ReviewDate.Month(YearMonth.of(2025, 1)), ReviewDate.BeforeYear(2024), null), reviews.external.map { it.written })
        assertEquals(reviews, repository.cachedReviews(100001))
    }

    @Test
    fun `keeps https source links and falls back for absent unsafe and malformed links`() = runTest {
        val links = listOf(" https://example.test/source ", "http://example.test/source", null, " ", "not a url", "https:///missing-host")
        val api = FakeApi().apply {
            result = result!!.copy(external = links.mapIndexed { index, link -> review(index + 1).copy(sourceLink = link) })
        }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope)

        val reviews = (repository.reviews(100001) as AppResult.Success).value

        assertEquals(listOf("https://example.test/source") + List(5) { PROVIDER_URL }, reviews.external.map { it.sourceUrl })
    }

    @Test
    fun `trims optional strings drops blank reviews and preserves full text and order`() = runTest {
        val api = FakeApi().apply {
            result = result!!.copy(external = listOf(
                review(1).copy(subjectTitle = " Предмет ", sourceTitle = " Источник ", text = " Первый\nвторой абзац "),
                review(2).copy(text = " \n\t "),
                review(3).copy(subjectTitle = " ", sourceTitle = " ", text = " Третий "),
            ))
        }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope)

        val reviews = (repository.reviews(100001) as AppResult.Success).value.external

        assertEquals(listOf(UUID(0, 1).toString(), UUID(0, 3).toString()), reviews.map { it.id })
        assertEquals(listOf("Первый\nвторой абзац", "Третий"), reviews.map { it.text })
        assertEquals("Предмет", reviews[0].subject)
        assertEquals("Источник", reviews[0].sourceTitle)
        assertNull(reviews[1].subject)
        assertNull(reviews[1].sourceTitle)
    }

    @Test
    fun `HTTP and network failures preserve the successful cache`() = runTest {
        val api = FakeApi().apply { result = result!!.copy(external = listOf(review(1))) }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope)
        val cached = (repository.reviews(100001) as AppResult.Success).value

        api.failure = HttpException(Response.error<Any>(404, "{}".toResponseBody()))
        assertEquals(AppResult.Failure(AppError.NotFound), repository.reviews(100001))
        assertEquals(cached, repository.cachedReviews(100001))
        api.failure = IOException("Synthetic offline response")
        assertEquals(AppResult.Failure(AppError.Network), repository.reviews(100001))
        assertEquals(cached, repository.cachedReviews(100001))
    }

    @Test
    fun `an absent Backend payload is a failure instead of an empty successful list`() = runTest {
        val api = FakeApi().apply { result = null }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope)

        assertTrue((repository.reviews(100001) as AppResult.Failure).error is AppError.Unknown)
        assertNull(repository.cachedReviews(100001))
    }

    @Test
    fun `opting out clears warm cache and reconnect does not resurrect it`() = runTest {
        val services = FakeServices(true)
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope)
        repository.reviews(100001)
        assertNotNull(repository.cachedReviews(100001))

        services.enabled.value = false
        runCurrent()
        assertNull(repository.cachedReviews(100001))
        services.enabled.value = true
        runCurrent()

        assertNull(repository.cachedReviews(100001))
        assertEquals(1, api.calls)
    }

    @Test
    fun `direct disabled gate clears caches before the observer runs`() = runTest {
        val services = FakeServices(true)
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope)
        repository.reviews(100001)

        services.enabled.value = false
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.reviews(100001))
        services.enabled.value = true
        runCurrent()

        assertNull(repository.cachedReviews(100001))
        assertEquals(1, api.calls)
    }

    @Test
    fun `clearing session data forgets cached reviews`() = runTest {
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope)
        repository.reviews(100001)
        assertNotNull(repository.cachedReviews(100001))

        repository.clearSessionData()

        assertNull(repository.cachedReviews(100001))
    }

    @Test
    fun `pending responses cannot report success or restore cache after opting out`() = runTest {
        val services = FakeServices(true)
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope)
        repository.reviews(100001)
        val gate = ResponseGate()
        api.beforeResponse = gate::await
        val pending = async { repository.reviews(100001) }
        gate.entered.await()

        services.enabled.value = false
        runCurrent()
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertNull(repository.cachedReviews(100001))
    }

    @Test
    fun `pre-disconnect responses cannot replace fresh reviews after reconnect`() = runTest {
        val services = FakeServices(true)
        val api = FakeApi().apply { result = result!!.copy(external = listOf(review(1))) }
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope)
        val gate = ResponseGate()
        api.beforeResponse = gate::await
        val pending = async { repository.reviews(100001) }
        gate.entered.await()
        services.enabled.value = false
        runCurrent()
        services.enabled.value = true
        runCurrent()
        assertNull(repository.cachedReviews(100001))

        api.beforeResponse = {}
        api.result = api.result!!.copy(external = listOf(review(2)))
        val fresh = (repository.reviews(100001) as AppResult.Success).value
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertEquals(fresh, repository.cachedReviews(100001))
        assertEquals(UUID(0, 2).toString(), fresh.external.single().id)
    }

    @Test
    fun `pre-clear responses cannot replace fresh session cache`() = runTest {
        val api = FakeApi().apply { result = result!!.copy(external = listOf(review(1))) }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope)
        val gate = ResponseGate()
        api.beforeResponse = gate::await
        val pending = async { repository.reviews(100001) }
        gate.entered.await()

        repository.clearSessionData()
        api.beforeResponse = {}
        api.result = api.result!!.copy(external = listOf(review(2)))
        val fresh = (repository.reviews(100001) as AppResult.Success).value
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertEquals(fresh, repository.cachedReviews(100001))
    }

    @Test
    fun `initial enabled observation accepts a matching suspended gate read`() = runTest {
        val services = FakeServices(true)
        val gate = CompletableDeferred<Unit>()
        services.readGate = { gate.await() }
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope)
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.reviews(100001) }
        runCurrent()
        assertEquals(0, api.calls)

        gate.complete(Unit)

        assertTrue(pending.await() is AppResult.Success)
        assertNotNull(repository.cachedReviews(100001))
    }

    @Test
    fun `a suspended enabled read cannot reach Backend after disconnect and reconnect`() = runTest {
        val services = FakeServices(true)
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.readGate = { gate.await() }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.reviews(100001) }

        services.enabled.value = false
        runCurrent()
        services.enabled.value = true
        runCurrent()
        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertNull(repository.cachedReviews(100001))
        assertEquals(0, api.calls)
    }

    @Test
    fun `a suspended disabled read cannot clear newer connected reviews`() = runTest {
        val services = FakeServices(false)
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.readGate = { gate.await() }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.reviews(100001) }
        services.enabled.value = true
        runCurrent()
        services.readGate = null
        val fresh = (repository.reviews(100001) as AppResult.Success).value

        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertEquals(fresh, repository.cachedReviews(100001))
        assertEquals(1, api.calls)
    }

    @Test
    fun `session cleanup invalidates a suspended opt-in read before the API call`() = runTest {
        val services = FakeServices(true)
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.readGate = { gate.await() }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.reviews(100001) }

        repository.clearSessionData()
        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertNull(repository.cachedReviews(100001))
        assertEquals(0, api.calls)
    }

    @Test
    fun `request cancellation remains cancellation`() = runTest {
        val api = FakeApi().apply { failure = CancellationException("Synthetic cancellation") }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope)

        try {
            repository.reviews(100001)
            fail("Cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertEquals("Synthetic cancellation", actual.message)
        }
    }

    private fun review(number: Int) = WireReview(UUID(0, number.toLong()), null, null, null, null, null, "Отзыв $number")

    private class FakeServices(enabled: Boolean) : CustomServicesRepository {
        val enabled = MutableStateFlow(enabled)
        var readGate: (suspend () -> Unit)? = null

        override fun observeEnabled(): Flow<Boolean> = enabled
        override suspend fun isEnabled(): Boolean {
            val on = enabled.value
            readGate?.invoke()
            return on
        }
        override suspend fun setEnabled(enabled: Boolean) { this.enabled.value = enabled }
    }

    private class ResponseGate {
        val entered = CompletableDeferred<Unit>()
        private val release = CountDownLatch(1)

        fun await() {
            entered.complete(Unit)
            assertTrue("Synthetic response gate was not released", release.await(10, TimeUnit.SECONDS))
        }

        fun open() = release.countDown()
    }

    private class FakeApi {
        var result: WireTeacherReviews? = WireTeacherReviews(100001, PROVIDER_URL, emptyList())
        var failure: Exception? = null
        var beforeResponse: () -> Unit = {}
        val requestedIsus = CopyOnWriteArrayList<Int>()
        private val callCount = AtomicInteger()
        val calls: Int get() = callCount.get()

        val instance: ItmoWidgetsApi = Proxy.newProxyInstance(
            ItmoWidgetsApi::class.java.classLoader,
            arrayOf(ItmoWidgetsApi::class.java),
        ) { proxy, method, arguments ->
            when (method.name) {
                "equals" -> return@newProxyInstance proxy === arguments?.firstOrNull()
                "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                "toString" -> return@newProxyInstance "FakeReviewsApi"
            }
            check(method.name == "teacherReviews") { "Unexpected ItmoWidgetsApi call: ${method.name}" }
            callCount.incrementAndGet()
            requestedIsus += arguments[0] as Int
            failure?.let { throw it }
            val response = ApiResponse.success(result)
            beforeResponse()
            response
        } as ItmoWidgetsApi
    }

    private companion object {
        const val PROVIDER_URL = "https://onetwozzzplus.github.io/reviews/#/teacher/100001"
    }
}
