package dev.alllexey.itmowidgets.feature.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStatus
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

fun homeLessonRow(pairId: Long = 1, state: HomeLessonState = HomeLessonState.NEXT) = HomeScheduleRow.Lesson(
    LessonDetailsArgs(
        pairId = pairId, date = "2026-09-07", subjectName = "Предмет $pairId", typeId = 1, format = "Очный",
        start = "09:30", end = "11:00", teacherFio = "Преподаватель", teacherIsu = 300001, room = "1506",
        building = "Кронверкский проспект, 49", buildingId = 13, mainBuildingId = 13, note = null,
        zoomUrl = null, zoomPassword = null, zoomInfo = null
    ),
    state
)

fun homeScheduleCard(vararg rows: HomeScheduleRow = arrayOf(homeLessonRow())) =
    HomeCard.Schedule(LocalDate.of(2026, 9, 7), tomorrow = false, rows.toList(), completed = 0)

class FakeHomeCardSource(vararg initial: HomeCard) : HomeCardSource {
    val cards = MutableStateFlow(initial.toList())
    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var pendingRefresh: CompletableDeferred<AppResult<Unit>>? = null
    var refreshes = 0
    var revalidations = 0

    override fun observe(): Flow<List<HomeCard>> = cards

    override suspend fun refresh(): AppResult<Unit> {
        refreshes++
        return pendingRefresh?.await() ?: refreshResult
    }

    override suspend fun revalidate() {
        revalidations++
    }
}

class FakeHomeCardPreferences : HomeCardPreferences {
    val hidden = MutableStateFlow<Set<HomeCardKind>>(emptySet())
    override fun observeHidden(): Flow<Set<HomeCardKind>> = hidden
}

class FakeHomeHintStore : HomeHintStore {
    val dismissed = MutableStateFlow<Set<HomeHint>>(emptySet())
    override fun observeDismissed(): Flow<Set<HomeHint>> = dismissed
    override suspend fun dismiss(hint: HomeHint) {
        dismissed.value = dismissed.value + hint
    }
}

class FakeHomeHintStatus : HomeHintStatus {
    var widgetPlaced = true
    var notifications = true
    override suspend fun anyWidgetPlaced() = widgetPlaced
    override suspend fun notificationsEnabled() = notifications
}
