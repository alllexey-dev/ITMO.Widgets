package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.session.SessionTokens
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetSnapshotStoreImpl
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleListWidgetItem
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleListWidgetItemKind
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetLesson
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetLessonState
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetContent
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetKind
import dev.alllexey.itmowidgets.upgrade.Captured22
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals

/** `no_backup/widgets/schedule_snapshot.json`: placed widgets keep their last content until the next refresh. */
object ScheduleWidgetSnapshotUpgrade {

    fun check(fixture: Upgrade22Fixture): Unit = runBlocking {
        val lesson = ScheduleWidgetLesson(
            Captured22.SUBJECT, "10:00", "11:30", 1, "Тестовый преподаватель", "101", "Тестовый корпус",
            ScheduleWidgetLessonState.UPCOMING
        )
        val expected = ScheduleWidgetSnapshot(
            singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.LESSON, lesson, remainingLessons = 2),
            lessonList = listOf(
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.HEADER, dateIso = "2026-10-05", tomorrow = true),
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.LESSON, lesson = lesson),
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.END)
            ),
            singleLessonStyle = LessonStyle.DOT,
            lessonListStyle = LessonStyle.LINE,
            compactTextSize = WidgetTextSize.LARGE,
            fullTextSize = WidgetTextSize.EXTRA_LARGE
        )
        val store = ScheduleWidgetSnapshotStoreImpl(
            gson = fixture.gson,
            context = fixture.context,
            scheduleChecks = ScheduleCheckPreferences(fixture.preferences),
            backend = OptedInBackend,
            timeProvider = FixedTime(fixture.clock),
            tokens = SignedInTokens
        )

        assertEquals(expected, store.read())
    }

    private object OptedInBackend : BackendGate {
        override suspend fun isConnected() = true
        override fun observeConnected(): Flow<Boolean> = flowOf(true)
        override suspend fun mayCallBackend() = true
        override suspend fun isOptedIn() = true
    }

    private class FixedTime(private val clock: Clock) : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), zoneId)
        override fun now(): OffsetDateTime = OffsetDateTime.ofInstant(clock.instant(), zoneId)
    }

    private object SignedInTokens : SessionTokenStore {
        override fun hasRefreshToken() = true
        override fun getIdToken() = Captured22.ID_TOKEN
        override fun replaceWithRefreshToken(refreshToken: String) = Unit
        override fun replaceWithTokens(tokens: SessionTokens) = Unit
        override fun clearTokens() = Unit
    }
}
