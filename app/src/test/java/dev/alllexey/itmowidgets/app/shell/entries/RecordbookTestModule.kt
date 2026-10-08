package dev.alllexey.itmowidgets.app.shell.entries

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.testing.FakeScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.testing.FakeSportScoreRepository
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLessonsGateway
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLinksRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSubjectDetails
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContextResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsPlanMarks
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkCheckResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkNews
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSource
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSubjectTarget
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.ReadStamp
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.SheetsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetInspection
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonsLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLinksLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTeacherLevelsLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The recordbook data of the shell tests on synthetic fakes: one program with three periods and one subject per
 * period, the recordbook tab's ViewModel, the subject page's and the scores sheet's. Every source answers at once;
 * [subjectRequests] records the semester of every subject list asked for, [bars] the BARS answer when [barsEnabled].
 * A sheet's reading fails on the network and no total is stored, so `Изменить итог` is done at once.
 */
internal class RecordbookTestGraph(var barsEnabled: Boolean = false) {
    val time: AcademicTimeProvider = FixedAcademicTime()
    val subjectRequests = mutableListOf<Int>()
    var bars: AppResult<List<RecordbookSubject>> = AppResult.Success(emptyList())

    private val repository = object : RecordbookRepository {
        override suspend fun getPrograms(): AppResult<List<RecordbookProgram>> = AppResult.Success(listOf(PROGRAM))
        override suspend fun getSubjects(programId: Long, semester: Int): AppResult<List<RecordbookSubject>> {
            subjectRequests += semester
            return AppResult.Success(listOf(subject(semester)))
        }
        override suspend fun getControls(entryId: Long): AppResult<List<RecordbookControl>> = AppResult.Success(emptyList())
    }
    private val barsRepository = object : BarsRecordbookRepository {
        override suspend fun getSubjects(period: RecordbookPeriod): AppResult<List<RecordbookSubject>> = bars
        override suspend fun getSubject(journal: BarsJournalReference): AppResult<BarsSubjectDetails> =
            AppResult.Failure(AppError.NotFound)
    }
    private val barsPreference = object : BarsPreferenceRepository {
        override suspend fun isEnabled() = barsEnabled
        override suspend fun setEnabled(enabled: Boolean): AppResult<Unit> {
            barsEnabled = enabled
            return AppResult.Success(Unit)
        }
    }
    private val sportResolver = RecordbookSportResolver(FakeSportScoreRepository())

    fun module(): Module = module {
        viewModel {
            RecordbookViewModel(
                repository, barsRepository, barsPreference, get<SavedStateHandle>(), sportResolver, time, NoMarks,
                NoSheets,
            )
        }
        viewModel {
            RecordbookSubjectViewModel(
                repository, barsRepository, get<SavedStateHandle>(), sportResolver, time, NoMarks,
                SubjectLessonsLoader(
                    FakeSubjectLessonsGateway(), FakeScheduleRefreshGateway(), NoBindings, SubjectContextResolver(), time,
                ),
                SubjectLinksLoader(FakeSubjectLinksRepository()),
                SubjectSheetLoader(NoSheets, time),
                SubjectTeacherLevelsLoader(NoLevels),
            )
        }
        viewModel { SheetScoresViewModel(get<SavedStateHandle>(), NoSheets) }
    }

    private object NoMarks : MarkTrackingRepository {
        override fun observeNews(): Flow<List<MarkNews>> = flowOf(emptyList())
        override suspend fun checkMyItmo(): AppResult<MarkCheckResult> = AppResult.Success(MarkCheckResult.Baseline)
        override suspend fun checkBars(): BarsCheck = BarsCheck.NoSession
        override suspend fun checkSheets() = SheetsCheck(MarkCheckResult.Baseline, emptyList())
        override fun readStarted() = ReadStamp(0)
        override suspend fun recordMyItmoSeen(
            stamp: ReadStamp,
            half: StudyHalf,
            programId: Long,
            semester: Int,
            subjects: List<RecordbookSubject>,
        ) = Unit
        override suspend fun recordBarsSeen(stamp: ReadStamp, half: StudyHalf, plans: List<BarsPlanMarks>) = Unit
        override suspend fun target(news: MarkNews, withBars: Boolean): MarkSubjectTarget? = null
        override suspend fun markNotified(ids: Set<String>) = Unit
        override suspend fun markRead(half: StudyHalf, nameKey: String) = Unit
        override suspend fun markAllRead() = Unit
        override suspend fun resetSource(source: MarkSource) = Unit
    }

    private object NoSheets : SheetScoresRepository {
        override fun observe(): Flow<List<SheetScore>> = flowOf(emptyList())
        override suspend fun refresh(scope: ResourceScope) = Unit
        override suspend fun inspect(url: String): SheetInspection = SheetInspection.Failed(SheetStatus.NETWORK)
        override suspend fun connect(scope: ResourceScope, url: String, row: SheetRowMatch, total: SheetCell) =
            AppResult.Success(Unit)
        override suspend fun changeTotal(scope: ResourceScope, row: SheetRowMatch, total: SheetCell) =
            AppResult.Success(Unit)
        override suspend fun disconnect(scope: ResourceScope) = Unit
        override suspend fun check(half: StudyHalf) = SheetCheck(emptyList(), emptyList())
        override suspend fun untrack() = Unit
    }

    private object NoBindings : SubjectBindingStore {
        override suspend fun get(disciplineId: Long): Long? = null
        override suspend fun put(disciplineId: Long, subjectId: Long) = Unit
        override suspend fun remove(disciplineId: Long) = Unit
    }

    private object NoLevels : TeacherLevelsRepository {
        override suspend fun levels(isus: Set<Int>): Map<Int, TeacherLevel> = emptyMap()
    }

    companion object {
        const val PROGRAM_ID = 1L
        const val CURRENT_SEMESTER = 3
        const val EARLIER_SEMESTER = 2
        const val STUDY_YEAR = "2026/2027"

        val PROGRAM = RecordbookProgram(
            PROGRAM_ID,
            "Тестовая программа",
            listOf(
                RecordbookPeriod(STUDY_YEAR, CURRENT_SEMESTER, 2, actual = true),
                RecordbookPeriod("2025/2026", EARLIER_SEMESTER, 1, actual = false),
                RecordbookPeriod("2025/2026", 1, 1, actual = false),
            ),
        )

        /** The one subject of [semester]; its entry id is the semester's tenfold. */
        fun entryId(semester: Int): Long = semester * 10L

        fun subject(semester: Int) = RecordbookSubject(
            "Тестовый предмет $semester", 100L + semester, entryId(semester), "Экзамен", 75.0, null, 1, null, true,
            "Тестовый преподаватель",
        )
    }
}
