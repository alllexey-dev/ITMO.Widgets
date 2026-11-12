package dev.alllexey.itmowidgets.feature.recordbook.data.lms

import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.InMemorySecureStore
import dev.alllexey.itmowidgets.feature.recordbook.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsScore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LmsScoresRepositoryImplTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `scores survive a fresh repository and are scoped to account and semester`() = runTest {
        var owner = 123
        val users = object : CurrentUserProvider {
            override suspend fun getCurrentUser() = CurrentUser(owner, null, null)
        }
        val store = InMemorySecureStore()
        val demo = FakeDemoMode()
        fun repository() = LmsScoresRepositoryImpl(store, users, demo, main.appDispatchers)
        val args = RecordbookSubjectArgs(entryId = 1, programId = 2, semester = 3, studyYear = "2026/2027")
        val scores = listOf(LmsScore("РК №1", 9.0, 10.0, "VERIFIED"))
        repository().save(args, scores)
        val reloaded = repository()
        assertEquals(scores, reloaded.load(args))
        assertEquals(emptyList<LmsScore>(), reloaded.load(args.copy(semester = 4)))
        owner = 456
        assertEquals(emptyList<LmsScore>(), reloaded.load(args))
        owner = 123
        reloaded.clearSessionData()
        assertEquals(emptyList<LmsScore>(), repository().load(args))
    }

    @Test fun `supports notification args, trims controlType and deletes empty saves`() = runTest {
        val users = object : CurrentUserProvider {
            override suspend fun getCurrentUser() = CurrentUser(123, null, null)
        }
        val store = InMemorySecureStore()
        val demo = FakeDemoMode()
        fun repository() = LmsScoresRepositoryImpl(store, users, demo, main.appDispatchers)

        val examArgs = RecordbookSubjectArgs(
            entryId = 1,
            programId = 2,
            semester = 3,
            studyYear = "2026/2027",
            disciplineId = 100L,
            controlType = "  Экзамен ",
        )
        val examScores = listOf(LmsScore("РК №1", 9.0, 10.0, "VERIFIED"))
        repository().save(examArgs, examScores)

        val notificationArgs = RecordbookSubjectArgs(entryId = 1, programId = 2, semester = 3, studyYear = "2026/2027")
        assertEquals(examScores, repository().load(notificationArgs))
        assertEquals(examScores, repository().load(examArgs.copy(controlType = "Экзамен")))

        val creditArgs = examArgs.copy(disciplineId = 200L, controlType = "Зачет")
        val creditScores = listOf(LmsScore("Тест", 15.0, 20.0, "VERIFIED"))
        repository().save(creditArgs, creditScores)

        // Ambiguous notification route returns empty rather than guessing between subjects.
        assertEquals(emptyList<LmsScore>(), repository().load(notificationArgs))
        assertEquals(creditScores, repository().load(creditArgs))

        repository().save(examArgs.copy(controlType = "Экзамен"), emptyList())
        assertEquals(emptyList<LmsScore>(), repository().load(examArgs))
        assertEquals(creditScores, repository().load(notificationArgs))
    }
}
