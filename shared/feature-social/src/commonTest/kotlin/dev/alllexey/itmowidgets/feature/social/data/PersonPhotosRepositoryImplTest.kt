package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class PersonPhotosRepositoryImplTest {
    @Test
    fun repeatedRowsShareOneLookupAndReuseThePersonCache() = runTest {
        val people = People()
        val photos = PersonPhotosRepositoryImpl(people, FakeDemoMode(), FakeSessionTokenStore())
        val results = List(12) { async { photos.photoUrl(123) } }.awaitAll()
        assertEquals(List(12) { "https://example.test/123.jpg" }, results)
        assertEquals(1, people.calls)
        assertEquals(results.first(), photos.cachedPhotoUrl(123))
        assertEquals(results.first(), photos.photoUrl(123))
        assertEquals(1, people.calls)
    }

    @Test
    fun requestsAreBoundedAndAPersonWithoutAPhotoIsCachedToo() = runTest {
        val people = People(noPhoto = true)
        val photos = PersonPhotosRepositoryImpl(people, FakeDemoMode(), FakeSessionTokenStore())
        (1..20).map { async { photos.photoUrl(it) } }.awaitAll()
        assertEquals(4, people.peak)
        assertNull(photos.photoUrl(1))
        assertEquals(20, people.calls)
    }

    @Test
    fun aConcurrentFailureIsSharedAndANewVisitCanRetry() = runTest {
        val people = People(fail = true)
        val photos = PersonPhotosRepositoryImpl(people, FakeDemoMode(), FakeSessionTokenStore())
        assertEquals(List(12) { null }, List(12) { async { photos.photoUrl(1) } }.awaitAll())
        assertEquals(1, people.calls)
        people.fail = false
        assertEquals("https://example.test/1.jpg", photos.photoUrl(1))
        assertEquals(2, people.calls)
    }

    @Test
    fun invalidIsusAndASignedOutSessionMakeNoLookupButTheDemoStillWorks() = runTest {
        val people = People()
        val demo = FakeDemoMode()
        val tokens = FakeSessionTokenStore(signedIn = false)
        val photos = PersonPhotosRepositoryImpl(people, demo, tokens)
        assertNull(photos.photoUrl(0))
        assertNull(photos.photoUrl(-1))
        assertNull(photos.photoUrl(123))
        assertEquals(0, people.calls)
        demo.active.value = true
        assertEquals("https://example.test/123.jpg", photos.photoUrl(123))
        assertEquals(1, people.calls)
    }

    @Test
    fun cancellationReleasesTheLookupAndANextReaderCanRetry() = runTest {
        val people = People()
        val photos = PersonPhotosRepositoryImpl(people, FakeDemoMode(), FakeSessionTokenStore())
        val cancelled = async { photos.photoUrl(1) }
        runCurrent()
        cancelled.cancelAndJoin()
        assertEquals(0, people.active)
        assertEquals("https://example.test/1.jpg", photos.photoUrl(1))
        assertEquals(2, people.calls)
    }

    @Test
    fun cancellingOneScreenDoesNotCancelAnotherReaderOfTheSameTeacher() = runTest {
        val people = People()
        val photos = PersonPhotosRepositoryImpl(people, FakeDemoMode(), FakeSessionTokenStore())
        val owner = async { photos.photoUrl(1) }
        val other = async { photos.photoUrl(1) }
        runCurrent()
        owner.cancelAndJoin()
        assertEquals("https://example.test/1.jpg", other.await())
        assertEquals(2, people.calls)
    }

    private class People(val noPhoto: Boolean = false, var fail: Boolean = false) : PersonRepository {
        val cache = mutableMapOf<Int, Person>()
        var calls = 0
        var active = 0
        var peak = 0
        override fun cachedPerson(isu: Int) = cache[isu]
        override suspend fun person(isu: Int): AppResult<Person> {
            calls++
            active++
            peak = maxOf(peak, active)
            try {
                delay(10)
                if (fail) return AppResult.Failure(AppError.Network)
                val person = Person(isu, "Teacher", if (noPhoto) null else "https://example.test/$isu.jpg", emptyList(), emptyList(), emptyList())
                cache[isu] = person
                return AppResult.Success(person)
            } finally { active-- }
        }
    }
}
