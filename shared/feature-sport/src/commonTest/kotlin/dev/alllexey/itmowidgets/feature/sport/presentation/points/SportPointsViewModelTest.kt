package dev.alllexey.itmowidgets.feature.sport.presentation.points

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttendance
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.shared.feature.sport.SportPlurals
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SportPointsViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUpMain() = main.install()

    @AfterTest
    fun tearDownMain() = main.reset()

    private val data = FakeSportDataRepository()
    private val time = FixedAcademicTime(LocalDateTime(2026, 10, 30, 12, 0))

    private fun award(type: String, score: Int, at: String, competition: String? = null, place: String? = null) = SportAttendance(
        type = type, name = SectionName("Плавание"), evaluationId = 1, evaluationName = null, sectionLevel = 1, score = score,
        dateTime = Instant.parse("${at}T18:00:00+03:00"), isCompetition = competition != null, competitionName = competition, place = place,
    )

    private fun score(vararg history: SportAttendance) = SportScore(
        attendances = history.filter { it.type == "lesson" }.sumOf { it.score },
        other = history.filter { it.type != "lesson" }.sumOf { it.score },
        attendancesData = history.toList(),
    )

    @Test
    fun theFormulaTheCapAndTheHistoryByMonthNewestFirst() = runTest(main.dispatcher) {
        data.score.emit(
            LoadState.Content(
                score(
                    award("lesson", 5, "2026-09-01"),
                    award("competition", 10, "2026-09-05", "Турнир по волейболу", "2 место"),
                    award("lesson", 5, "2026-10-02"),
                ),
            ),
        )
        val viewModel = SportPointsViewModel(data, time)
        advanceUntilIdle()

        val content = viewModel.uiState.value as SportPointsUiState.Content
        assertEquals(20, content.score.total)
        assertEquals(UiText.Plural(SportPlurals.sport_points_visits, 2, listOf(2)), content.visitsLabel)
        assertEquals(UiText.Res(SportStrings.sport_points_cap_value, listOf(10, 40)), content.cap)
        assertNull(content.done, "no forecast below 100")
        assertEquals(listOf(UiText.Dynamic("Октябрь"), UiText.Dynamic("Сентябрь")), content.months.map { it.title })
        val tournament = content.months[1].rows.first()
        assertEquals("Турнир по волейболу", tournament.title)
        assertEquals("+10", tournament.points)
        assertEquals(UiText.Joined(listOf(UiText.Dynamic("сб, 5 сент."), UiText.Dynamic("2 место")), ", "), tournament.subtitle)
        assertEquals(listOf(null, 2, 1), content.chips.map { it.count })
    }

    @Test
    fun theCreditDayIsWhenVisitsAndTheCappedBonusFirstReach100() = runTest(main.dispatcher) {
        val visits = (1..12).map { award("lesson", 5, "2026-09-${(it + 10).toString().padStart(2, '0')}") }
        data.score.emit(LoadState.Content(score(*(visits + award("competition", 50, "2026-09-02", "Кубок") + award("lesson", 5, "2026-10-28")).toTypedArray())))
        val viewModel = SportPointsViewModel(data, time)
        advanceUntilIdle()

        val content = viewModel.uiState.value as SportPointsUiState.Content
        assertEquals(UiText.Res(SportStrings.sport_points_done_title, listOf("22 сентября")), content.done, "the bonus counts 40 of its 50")
        assertEquals(UiText.Res(SportStrings.sport_points_cap_value, listOf(40, 40)), content.cap)
    }

    @Test
    fun theChipsShowVisitsOrBonusesOnly() = runTest(main.dispatcher) {
        data.score.emit(LoadState.Content(score(award("lesson", 5, "2026-09-01"), award("exercise", 4, "2026-09-03"))))
        val viewModel = SportPointsViewModel(data, time)
        advanceUntilIdle()

        viewModel.select(SportPointsFilter.BONUS)
        advanceUntilIdle()
        val content = viewModel.uiState.value as SportPointsUiState.Content
        assertEquals(listOf(true), content.months.flatMap { it.rows }.map { it.bonus })
        assertEquals(SportPointsFilter.BONUS, content.chips.single { it.selected }.filter)
    }

    @Test
    fun aFailedScoreIsAnError() = runTest(main.dispatcher) {
        data.score.emit(LoadState.Error(AppError.Network))
        val viewModel = SportPointsViewModel(data, time)
        advanceUntilIdle()
        assertEquals(SportPointsUiState.Failed(AppError.Network), viewModel.uiState.value)
    }
}
