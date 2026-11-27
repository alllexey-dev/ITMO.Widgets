package dev.alllexey.itmowidgets.feature.me.moderation.data

import dev.alllexey.itmowidgets.client.moderation.ModerationNoticeLink
import dev.alllexey.itmowidgets.client.moderation.ModerationNoticeReview
import dev.alllexey.itmowidgets.client.moderation.ModerationNoticesApi
import dev.alllexey.itmowidgets.client.moderation.ModerationOutcome
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.me.moderation.data.bell.ModerationBellSource
import dev.alllexey.itmowidgets.feature.me.moderation.domain.NoticeOutcome
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import dev.alllexey.itmowidgets.client.moderation.ModerationNotice as WireNotice

/** Backend's moderation notices: the gate, what this build can show, the bell rows and their texts. */
class ModerationNoticesTest {

    private val link = ModerationNoticeLink(Uuid.parse(LINK), 501, "Математический анализ", "2026-1", "Ведомость", "docs.google.com")
    private val review = ModerationNoticeReview(Uuid.parse(REVIEW), 200001, "Преподаватель Тестовый", null)
    private val api = FakeApi()

    private fun TestScope.repository(optedIn: Boolean = true, demo: Boolean = false) = StandardTestDispatcher(testScheduler).let { dispatcher ->
        ModerationNoticesRepositoryImpl(
            FakeBackendGate(optedIn),
            api,
            FakeDemoMode(demo),
            AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher),
        )
    }

    @Test
    fun theDemoAndAMissingOptInNeverCallBackend() = runTest {
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), repository(demo = true).refresh())
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository(optedIn = false).refresh())
        assertEquals(0, api.calls)
    }

    @Test
    fun anUnknownOutcomeAndANoticeWithoutASubjectAreLeftOut() = runTest {
        api.answer = listOf(
            wire(1, ModerationOutcome.REJECTED, note = "Не по предмету", link = link),
            wire(2, ModerationOutcome.UNKNOWN, link = link),
            wire(3, ModerationOutcome.APPROVED),
            wire(4, ModerationOutcome.APPROVED, link = link, review = review),
        )
        val repository = repository()

        val notices = (repository.refresh() as AppResult.Success).value

        assertEquals(listOf(uuid(1)), notices.map { it.id })
        assertEquals(NoticeOutcome.REJECTED, notices.single().outcome)
        assertEquals(notices, repository.notices.value)
    }

    @Test
    fun aPushedNoticeJoinsTheListOnceAndTheSessionClearsIt() = runTest {
        api.answer = listOf(wire(1, ModerationOutcome.APPROVED, link = link, at = "2026-11-20T09:00:00Z"))
        val repository = repository()
        repository.refresh()
        val pushed = wire(2, ModerationOutcome.HIDDEN, review = review, at = "2026-11-21T09:00:00Z").toDomain()!!

        repository.received(pushed)
        repository.received(pushed)

        assertEquals(listOf(uuid(2), uuid(1)), repository.notices.value!!.map { it.id })
        repository.clearSessionData()
        assertNull(repository.notices.value)
    }

    @Test
    fun bellRowsOpenTheLinksOrTheTeacherAndNameTheOutcome() = runTest {
        api.answer = listOf(
            wire(1, ModerationOutcome.REJECTED, note = "Не по предмету", link = link),
            wire(2, ModerationOutcome.APPROVED, note = "ignored", review = review),
        )
        val source = ModerationBellSource(repository())

        assertEquals(AppResult.Success(Unit), source.refresh())
        val (rejected, approved) = source.observe().first()

        assertEquals("moderation:${uuid(1)}", rejected.id)
        assertEquals(BellKind.LINK_MODERATION, rejected.kind)
        assertTrue(rejected.failed)
        assertEquals(UiText.Res(CoreStrings.notification_moderation_link_rejected), rejected.title)
        assertEquals(
            UiText.Res(CoreStrings.notification_moderation_link_body_note, listOf("Ведомость", "Математический анализ", "Не по предмету")),
            rejected.text,
        )
        assertEquals(
            EntryRoute(AppTab.HOME, overlay = AppRoutes.SubjectLinks(SubjectLinksArgs(501, "Математический анализ", "2026-1"))),
            rejected.target,
        )

        assertEquals(BellKind.REVIEW_MODERATION, approved.kind)
        assertFalse(approved.failed)
        assertEquals(UiText.Res(CoreStrings.notification_moderation_review_approved), approved.title)
        assertEquals(UiText.Res(CoreStrings.notification_moderation_review_body, listOf("Преподаватель Тестовый")), approved.text)
        assertEquals(EntryRoute(AppTab.HOME, overlay = AppRoutes.UserProfile(200001)), approved.target)
    }

    @Test
    fun aLinkWithoutATitleIsNamedByItsHostAndANamelessReviewByItsSubjectOrGenerically() {
        val untitled = wire(1, ModerationOutcome.RESTORED, link = link.copy(title = " ")).toDomain()!!
        assertEquals(UiText.Res(CoreStrings.notification_moderation_link_body, listOf("docs.google.com", "Математический анализ")), untitled.text)

        val bySubject = wire(2, ModerationOutcome.HIDDEN, review = review.copy(teacherName = null, subjectTitle = "Физика")).toDomain()!!
        assertEquals(UiText.Res(CoreStrings.notification_moderation_review_body, listOf("Физика")), bySubject.text)

        val nameless = review.copy(teacherName = null)
        assertEquals(
            UiText.Res(CoreStrings.notification_moderation_review_note, listOf("Оскорбления")),
            wire(3, ModerationOutcome.REJECTED, note = "Оскорбления", review = nameless).toDomain()!!.text,
        )
        assertEquals(
            UiText.Res(CoreStrings.notification_moderation_review_unnamed),
            wire(4, ModerationOutcome.APPROVED, review = nameless).toDomain()!!.text,
        )
    }

    @Test
    fun withoutTheOptInTheBellStaysQuiet() = runTest {
        val source = ModerationBellSource(repository(optedIn = false))

        assertEquals(AppResult.Success(Unit), source.refresh())
        assertTrue(source.observe().first().isEmpty())
    }

    private fun wire(
        n: Int,
        outcome: ModerationOutcome,
        note: String? = null,
        link: ModerationNoticeLink? = null,
        review: ModerationNoticeReview? = null,
        at: String = "2026-11-20T09:00:00Z",
    ) = WireNotice(Uuid.parse(uuid(n)), outcome, note, Instant.parse(at), link, review)

    private fun uuid(n: Int) = "00000000-0000-4000-8000-" + (900 + n).toString().padStart(12, '0')

    private class FakeApi : ModerationNoticesApi {
        var answer: List<WireNotice> = emptyList()
        var calls = 0

        override suspend fun notices(): List<WireNotice> {
            calls++
            return answer
        }
    }

    private companion object {
        const val LINK = "00000000-0000-4000-8000-000000000101"
        const val REVIEW = "00000000-0000-4000-8000-000000000201"
    }
}
