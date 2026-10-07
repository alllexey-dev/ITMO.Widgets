package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.reviews.SaveTeacherReviewRequest
import dev.alllexey.itmowidgets.client.reviews.SummaryLevel
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsApi
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsResponse
import dev.alllexey.itmowidgets.client.reviews.TeacherSummaryLevel
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.testkit.fakeFileSystemOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.IOException
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class TeacherLevelsRepositoryImplTest {

    private val fileSystem: FakeFileSystem = fakeFileSystemOf()
    private val directory = "/files/teacher_levels".toPath()
    private val file = directory / "levels.json"
    private val clock = MutableClock(Instant.parse("2026-09-29T10:00:00Z"))
    private val services = FakeBackendGate(optedIn = true)
    private val api = FakeTeacherLevelsApi()

    @AfterTest
    fun tearDown() {
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun theDemoKnowsTheTonesOfItsTeachersWithoutBackend() = runTest {
        services.optedIn.value = false
        val teachers = setOf(DemoPeople.ALGORITHMS_TEACHER.isu, DemoPeople.ENGLISH_TEACHER.isu)

        val levels = repository(FakeDemoMode(active = true)).levels(teachers)

        assertEquals(mapOf(DemoPeople.ALGORITHMS_TEACHER.isu to TeacherLevel.VERY_POSITIVE), levels)
        assertTrue(api.requests.isEmpty())
        assertFalse(fileSystem.exists(directory))
    }

    /** Every slot on the test's scheduler, so `runTest` runs the repository's `withContext` hops in order. */
    private fun TestScope.repository(demo: DemoMode = noDemo()) = TeacherLevelsRepositoryImpl(
        services, api, TeacherLevelsFileStore(directory, fileSystem), clock, demo,
        StandardTestDispatcher(testScheduler).let { AppDispatchers(io = it, default = it, main = it) },
    )

    @Test
    fun aDisabledOptInAnswersNothingWithoutARequestAndErasesTheFile() = runTest {
        api.levels[100001] = SummaryLevel.POSITIVE
        val repository = repository()
        repository.levels(setOf(100001))
        assertTrue(fileSystem.exists(file))

        services.optedIn.value = false

        assertEquals(emptyMap<Int, TeacherLevel>(), repository.levels(setOf(100001)))
        assertEquals(1, api.requests.size)
        assertFalse(fileSystem.exists(directory))
    }

    @Test
    fun answersAreKeptForADayAndAskedAgainAfterIt() = runTest {
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
    fun anAnswerIsFreshUntil1MsBeforeADayAndAskedAgainAtExactlyADay() = runTest {
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
    fun theCacheSurvivesANewRepositoryInstance() = runTest {
        api.levels[100001] = SummaryLevel.NEGATIVE
        repository().levels(setOf(100001))

        assertEquals(mapOf(100001 to TeacherLevel.NEGATIVE), repository().levels(setOf(100001)))
        assertEquals(1, api.requests.size)
    }

    @Test
    fun aTeacherWithoutALevelIsRememberedAndOnlyMissingTeachersAreAsked() = runTest {
        api.levels[100001] = SummaryLevel.VERY_POSITIVE
        val repository = repository()
        assertEquals(mapOf(100001 to TeacherLevel.VERY_POSITIVE), repository.levels(setOf(100001, 100002)))

        assertEquals(mapOf(100001 to TeacherLevel.VERY_POSITIVE), repository.levels(setOf(100001, 100002, 100003)))

        assertEquals(listOf(listOf(100001, 100002), listOf(100003)), api.requests)
    }

    @Test
    fun manyTeachersGoInBatchesOfFiftyAndInvalidISUsAreNeverSent() = runTest {
        val isus = (100001..100120).toSet()
        api.levels[100120] = SummaryLevel.MIXED

        val levels = repository().levels(isus + setOf(0, -5, 99_999, 10_000_000))

        assertEquals(mapOf(100120 to TeacherLevel.MIXED), levels)
        assertEquals(listOf(50, 50, 20), api.requests.map { it.size })
        assertEquals(isus, api.requests.flatten().toSet())
    }

    @Test
    fun aToneThisAppDoesNotKnowIsRememberedAsNoLevel() = runTest {
        api.levels[100001] = SummaryLevel.UNKNOWN
        api.levels[100002] = SummaryLevel.NEGATIVE
        val repository = repository()

        assertEquals(mapOf(100002 to TeacherLevel.NEGATIVE), repository.levels(setOf(100001, 100002)))
        assertEquals(mapOf(100002 to TeacherLevel.NEGATIVE), repository.levels(setOf(100001, 100002)))
        assertEquals(1, api.requests.size)
    }

    @Test
    fun anISUOutsideTheAskedBatchInTheReplyIsIgnored() = runTest {
        api.extra = TeacherSummaryLevel(100009, SummaryLevel.VERY_NEGATIVE)
        val repository = repository()

        assertEquals(emptyMap<Int, TeacherLevel>(), repository.levels(setOf(100001)))
        api.extra = null
        assertEquals(emptyMap<Int, TeacherLevel>(), repository.levels(setOf(100009)))
        assertEquals(listOf(listOf(100001), listOf(100009)), api.requests)
    }

    @Test
    fun aFailedRequestReturnsTheFreshCacheAndWritesNothing() = runTest {
        api.levels[100001] = SummaryLevel.POSITIVE
        val repository = repository()
        repository.levels(setOf(100001))
        val written = fileSystem.read(file) { readUtf8() }

        api.failure = BackendException.Transport(IOException("Synthetic offline response"))
        assertEquals(mapOf(100001 to TeacherLevel.POSITIVE), repository.levels(setOf(100001, 100002)))
        assertEquals(written, fileSystem.read(file) { readUtf8() })

        clock.advance(2.days)
        assertEquals(emptyMap<Int, TeacherLevel>(), repository.levels(setOf(100001)))
        assertEquals(written, fileSystem.read(file) { readUtf8() })
    }

    @Test
    fun aCorruptFileIsErasedAndAskedAgain() = runTest {
        fileSystem.createDirectories(directory)
        fileSystem.write(file) { writeUtf8("{") }
        api.levels[100001] = SummaryLevel.MIXED

        assertEquals(mapOf(100001 to TeacherLevel.MIXED), repository().levels(setOf(100001)))
        assertEquals(1, api.requests.size)
        assertTrue(fileSystem.read(file) { readUtf8() }.contains("MIXED"))
    }

    @Test
    fun clearingSessionDataErasesTheCache() = runTest {
        api.levels[100001] = SummaryLevel.POSITIVE
        val repository = repository()
        repository.levels(setOf(100001))

        repository.clearSessionData()

        assertFalse(fileSystem.exists(directory))
        repository.levels(setOf(100001))
        assertEquals(2, api.requests.size)
    }

    @Test
    fun anAnswerArrivingAfterTheOptInWasDisabledIsNotWritten() = runTest {
        api.levels[100001] = SummaryLevel.POSITIVE
        api.beforeResponse = { services.optedIn.value = false }

        assertEquals(emptyMap<Int, TeacherLevel>(), repository().levels(setOf(100001)))
        assertFalse(fileSystem.exists(file))
    }

    private class MutableClock(private var now: Instant) : Clock {
        fun advance(duration: Duration) { now += duration }
        override fun now(): Instant = now
    }

    private class FakeTeacherLevelsApi : TeacherReviewsApi {
        val levels = mutableMapOf<Int, SummaryLevel>()
        val requests = mutableListOf<List<Int>>()
        var failure: Exception? = null
        var extra: TeacherSummaryLevel? = null
        var beforeResponse: () -> Unit = {}

        override suspend fun teacherSummaryLevels(isus: List<Int>): List<TeacherSummaryLevel> {
            requests += isus
            failure?.let { throw it }
            beforeResponse()
            return isus.mapNotNull { isu -> levels[isu]?.let { TeacherSummaryLevel(isu, it) } } + listOfNotNull(extra)
        }

        override suspend fun teacherReviews(isu: Int): TeacherReviewsResponse = unexpected()
        override suspend fun saveMyTeacherReview(isu: Int, request: SaveTeacherReviewRequest): TeacherReviewsResponse = unexpected()
        override suspend fun deleteMyTeacherReview(isu: Int): TeacherReviewsResponse = unexpected()
        override suspend fun voteTeacherReview(id: Uuid, request: ResourceVoteRequest): TeacherReviewsResponse = unexpected()
        override suspend fun reportTeacherReview(id: Uuid, request: ModerationReportRequest): TeacherReviewsResponse = unexpected()

        private fun unexpected(): Nothing = throw AssertionError("Levels ask only for summary levels")
    }
}
