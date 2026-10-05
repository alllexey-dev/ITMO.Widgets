package dev.alllexey.itmowidgets.feature.reviews.data

import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.reviews.SummaryLevel
import dev.alllexey.itmowidgets.core.model.reviews.TeacherSummaryLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TeacherLevelsRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "teacher_levels")
    private val file get() = File(directory, "levels.json")
    private val clock = MutableClock(Instant.parse("2026-09-29T10:00:00Z"))
    private val services = FakeBackendGate(optedIn = true)
    private val api = FakeTeacherLevelsApi()

    @Test
    fun `the demo knows the tones of its teachers without Backend`() = runTest {
        services.optedIn.value = false
        val teachers = setOf(DemoPeople.ALGORITHMS_TEACHER.isu, DemoPeople.ENGLISH_TEACHER.isu)

        val levels = repository(FakeDemoMode(active = true)).levels(teachers)

        assertEquals(mapOf(DemoPeople.ALGORITHMS_TEACHER.isu to TeacherLevel.VERY_POSITIVE), levels)
        assertTrue(api.requests.isEmpty())
        assertFalse(directory.exists())
    }

    private fun repository(demo: DemoMode = noDemo()) =
        TeacherLevelsRepositoryImpl(services, api.instance, TeacherLevelsFileStore(directory, Gson()), clock, demo, dispatchers)

    @Test
    fun `a disabled opt-in answers nothing without a request and erases the file`() = runTest {
        api.levels[100001] = SummaryLevel.POSITIVE
        val repository = repository()
        repository.levels(setOf(100001))
        assertTrue(file.exists())

        services.optedIn.value = false

        assertEquals(emptyMap<Int, TeacherLevel>(), repository.levels(setOf(100001)))
        assertEquals(1, api.requests.size)
        assertFalse(directory.exists())
    }

    @Test
    fun `answers are kept for a day and asked again after it`() = runTest {
        api.levels[100001] = SummaryLevel.POSITIVE
        val repository = repository()

        assertEquals(mapOf(100001 to TeacherLevel.POSITIVE), repository.levels(setOf(100001)))
        clock.advance(23.hours + 59.minutes)
        assertEquals(mapOf(100001 to TeacherLevel.POSITIVE), repository.levels(setOf(100001)))
        assertEquals(1, api.requests.size)

        api.levels[100001] = SummaryLevel.MIXED
        clock.advance(2.minutes)
        assertEquals(mapOf(100001 to TeacherLevel.MIXED), repository.levels(setOf(100001)))
        assertEquals(listOf(listOf(100001), listOf(100001)), api.requests)
    }

    @Test
    fun `an answer is fresh until 1 ms before a day and asked again at exactly a day`() = runTest {
        api.levels[100001] = SummaryLevel.POSITIVE
        val repository = repository()
        repository.levels(setOf(100001))

        clock.advance(1.days - 1.milliseconds)
        assertEquals(mapOf(100001 to TeacherLevel.POSITIVE), repository.levels(setOf(100001)))
        assertEquals(1, api.requests.size)

        api.levels[100001] = SummaryLevel.MIXED
        clock.advance(1.milliseconds)
        assertEquals(mapOf(100001 to TeacherLevel.MIXED), repository.levels(setOf(100001)))
        assertEquals(2, api.requests.size)
    }

    @Test
    fun `the cache survives a new repository instance`() = runTest {
        api.levels[100001] = SummaryLevel.NEGATIVE
        repository().levels(setOf(100001))

        assertEquals(mapOf(100001 to TeacherLevel.NEGATIVE), repository().levels(setOf(100001)))
        assertEquals(1, api.requests.size)
    }

    @Test
    fun `a teacher without a level is remembered and only missing teachers are asked`() = runTest {
        api.levels[100001] = SummaryLevel.VERY_POSITIVE
        val repository = repository()
        assertEquals(mapOf(100001 to TeacherLevel.VERY_POSITIVE), repository.levels(setOf(100001, 100002)))

        assertEquals(mapOf(100001 to TeacherLevel.VERY_POSITIVE), repository.levels(setOf(100001, 100002, 100003)))

        assertEquals(listOf(listOf(100001, 100002), listOf(100003)), api.requests)
    }

    @Test
    fun `many teachers go in batches of fifty and invalid ISUs are never sent`() = runTest {
        val isus = (100001..100120).toSet()
        api.levels[100120] = SummaryLevel.MIXED

        val levels = repository().levels(isus + setOf(0, -5, 99_999, 10_000_000))

        assertEquals(mapOf(100120 to TeacherLevel.MIXED), levels)
        assertEquals(listOf(50, 50, 20), api.requests.map { it.size })
        assertEquals(isus, api.requests.flatten().toSet())
    }

    @Test
    fun `an ISU outside the asked batch in the reply is ignored`() = runTest {
        api.extra = TeacherSummaryLevel(100009, SummaryLevel.VERY_NEGATIVE)
        val repository = repository()

        assertEquals(emptyMap<Int, TeacherLevel>(), repository.levels(setOf(100001)))
        api.extra = null
        assertEquals(emptyMap<Int, TeacherLevel>(), repository.levels(setOf(100009)))
        assertEquals(listOf(listOf(100001), listOf(100009)), api.requests)
    }

    @Test
    fun `a failed request returns the fresh cache and writes nothing`() = runTest {
        api.levels[100001] = SummaryLevel.POSITIVE
        val repository = repository()
        repository.levels(setOf(100001))
        val written = file.readText()

        api.failure = IOException("Synthetic offline response")
        assertEquals(mapOf(100001 to TeacherLevel.POSITIVE), repository.levels(setOf(100001, 100002)))
        assertEquals(written, file.readText())

        clock.advance(2.days)
        assertEquals(emptyMap<Int, TeacherLevel>(), repository.levels(setOf(100001)))
        assertEquals(written, file.readText())
    }

    @Test
    fun `a corrupt file is erased and asked again`() = runTest {
        directory.mkdirs()
        file.writeText("{")
        api.levels[100001] = SummaryLevel.MIXED

        assertEquals(mapOf(100001 to TeacherLevel.MIXED), repository().levels(setOf(100001)))
        assertEquals(1, api.requests.size)
        assertTrue(file.readText().contains("MIXED"))
    }

    @Test
    fun `clearing session data erases the cache`() = runTest {
        api.levels[100001] = SummaryLevel.POSITIVE
        val repository = repository()
        repository.levels(setOf(100001))

        repository.clearSessionData()

        assertFalse(directory.exists())
        repository.levels(setOf(100001))
        assertEquals(2, api.requests.size)
    }

    @Test
    fun `an answer arriving after the opt-in was disabled is not written`() = runTest {
        api.levels[100001] = SummaryLevel.POSITIVE
        api.beforeResponse = { services.optedIn.value = false }

        assertEquals(emptyMap<Int, TeacherLevel>(), repository().levels(setOf(100001)))
        assertFalse(file.exists())
    }

    private class MutableClock(private var now: Instant) : Clock {
        fun advance(duration: Duration) { now += duration }
        override fun now(): Instant = now
    }

    private class FakeTeacherLevelsApi {
        val levels = mutableMapOf<Int, SummaryLevel>()
        val requests = CopyOnWriteArrayList<List<Int>>()
        var failure: Exception? = null
        var extra: TeacherSummaryLevel? = null
        var beforeResponse: () -> Unit = {}

        @Suppress("UNCHECKED_CAST")
        val instance: ItmoWidgetsApi = Proxy.newProxyInstance(
            ItmoWidgetsApi::class.java.classLoader,
            arrayOf(ItmoWidgetsApi::class.java),
        ) { proxy, method, arguments ->
            when (method.name) {
                "equals" -> return@newProxyInstance proxy === arguments?.firstOrNull()
                "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                "toString" -> return@newProxyInstance "FakeLevelsApi"
            }
            check(method.name == "teacherSummaryLevels") { "Unexpected ItmoWidgetsApi call: ${method.name}" }
            val isus = arguments[0] as List<Int>
            requests += isus
            failure?.let { throw it }
            beforeResponse()
            ApiResponse.success(isus.mapNotNull { isu -> levels[isu]?.let { TeacherSummaryLevel(isu, it) } } + listOfNotNull(extra))
        } as ItmoWidgetsApi
    }
}
