package dev.alllexey.itmowidgets.feature.recordbook.data.lesson

import dev.alllexey.itmowidgets.core.lesson.LessonSubject
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLinksRepository
import dev.alllexey.itmowidgets.core.testing.linksSnapshot
import dev.alllexey.itmowidgets.core.testing.subjectLink
import dev.alllexey.itmowidgets.feature.recordbook.FakeRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContextResolver
import dev.alllexey.itmowidgets.feature.recordbook.recordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordbookLessonSubjectsTest {
    private val repository = FakeRecordbookRepository().apply {
        cachedPrograms = listOf(recordbookProgram())
        cachedSubjects = listOf(recordbookSubject(id = 42L, name = "Тестовый предмет"))
    }
    private val bindings = FakeSubjectBindingStore()
    private val links = FakeSubjectLinksRepository().apply {
        state.value = SubjectLinksState.Content(
            linksSnapshot(
                mine = listOf(subjectLink("own"), subjectLink("both")),
                shared = listOf(subjectLink("both", isMine = false), subjectLink("chat", LinkCategory.CHAT, isMine = false)),
                previous = listOf(subjectLink("old")),
            ),
        )
    }
    private val gate = FakeBackendGate(optedIn = true)
    private val autumn = LocalDate(2026, 9, 7)
    private val args = RecordbookSubjectArgs(entryId = 42L, programId = 1L, semester = 3, studyYear = "2026/2027")
    private val known = LessonSubject(AppRoutes.RecordbookSubject(args), 3, AppRoutes.RecordbookSubject(args, materials = true))

    private fun source(backendGate: FakeBackendGate = gate) =
        RecordbookLessonSubjects(repository, bindings, SubjectContextResolver(), links, backendGate)

    @Test fun `the same id opens the subject of the lesson's half-year from the cache with its current links`() = runTest {
        assertEquals(known, source().subject(1L, "Другое название", autumn))
        assertEquals(0, repository.programRequests)
        assertEquals(emptyList<Int>(), repository.subjectRequests)
        assertEquals(0, links.refreshes)
    }

    @Test fun `a confirmed binding and the one subject of the name match, a binding elsewhere does not`() = runTest {
        bindings.bindings[1L] = 555L
        assertEquals(known, source().subject(555L, "Совсем другое", autumn))
        assertNull(source().subject(777L, "Тестовый  ПРЕДМЕТ", autumn))

        bindings.bindings.clear()
        assertEquals(known, source().subject(777L, "Тестовый  ПРЕДМЕТ", autumn))
    }

    @Test fun `a subject the record book does not know has no actions`() = runTest {
        assertNull(source().subject(777L, "Неизвестный предмет", autumn))
    }

    @Test fun `without a cache the record book is asked once for the half-year's periods`() = runTest {
        repository.cachedPrograms = null
        repository.cachedSubjects = null
        assertEquals(known, source().subject(1L, "Тестовый предмет", autumn))
        assertEquals(1, repository.programRequests)
        assertEquals(listOf(3), repository.subjectRequests)
    }

    @Test fun `without the connection the subject opens with no links`() = runTest {
        assertEquals(known.copy(links = 0), source(FakeBackendGate(optedIn = false)).subject(1L, "Тестовый предмет", autumn))
        assertEquals(0, links.refreshes)
    }

    @Test fun `the demo session counts the demo links without Backend`() = runTest {
        val demo = FakeBackendGate(optedIn = false, demo = FakeDemoMode(active = true))
        assertEquals(known, source(demo).subject(1L, "Тестовый предмет", autumn))
        assertEquals(0, links.refreshes)
    }
}
