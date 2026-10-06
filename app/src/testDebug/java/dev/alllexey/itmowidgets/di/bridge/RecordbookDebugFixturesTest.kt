package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.testing.FakeScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.testing.FakeSportScoreRepository
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLessonsGateway
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLinksRepository
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLevelsRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsPreferenceRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.DataStoreSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsRecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarkTrackingRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.sheets.SheetScoresRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewFixtures
import dev.alllexey.itmowidgets.feature.resources.data.SubjectLinksRepositoryImpl
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherLevelsRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.SubjectLessonsGatewayImpl
import dev.alllexey.itmowidgets.feature.schedule.data.repository.ScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScoreRepositoryImpl
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.experimental.LazyApplication
import org.robolectric.annotation.experimental.LazyApplication.LazyLoad

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class RecordbookDebugFixturesTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `a fixture replaces every bridged type of the recordbook until its host unloads it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val recordbookBefore = koin.get<RecordbookRepository>()
        val barsBefore = koin.get<BarsRecordbookRepository>()
        val barsPreferenceBefore = koin.get<BarsPreferenceRepository>()
        val marksBefore = koin.get<MarkTrackingRepository>()
        val sheetsBefore = koin.get<SheetScoresRepository>()
        val bindingsBefore = koin.get<SubjectBindingStore>()

        val fixture = RecordbookDebugFixtures.load(application, Fakes)
        assertSame(Fakes.recordbook, koin.get<RecordbookRepository>())
        assertSame(Fakes.bars, koin.get<BarsRecordbookRepository>())
        assertSame(Fakes.barsPreference, koin.get<BarsPreferenceRepository>())
        assertSame(Fakes.marks, koin.get<MarkTrackingRepository>())
        assertSame(Fakes.sheets, koin.get<SheetScoresRepository>())
        assertSame(Fakes.bindings, koin.get<SubjectBindingStore>())
        assertSame(Fakes.sport, koin.get<SportScoreRepository>())
        assertSame(Fakes.lessons, koin.get<SubjectLessonsGateway>())
        assertSame(Fakes.scheduleRefresh, koin.get<ScheduleRefreshGateway>())
        assertSame(Fakes.links, koin.get<SubjectLinksRepository>())
        assertSame(Fakes.levels, koin.get<TeacherLevelsRepository>())
        assertSame(Fakes.time, koin.get<AcademicTimeProvider>())

        RecordbookDebugFixtures.unload(application, fixture)
        val core = CoreBridgeEntryPoint.from(application)
        // The module's own instances again, not a second cache or BARS client beside the ones Hilt-built code holds.
        assertSame(recordbookBefore, koin.get<RecordbookRepository>())
        assertSame(koin.get<RecordbookRepositoryImpl>(), koin.get<RecordbookRepository>())
        assertSame(barsBefore, koin.get<BarsRecordbookRepository>())
        assertSame(koin.get<BarsRecordbookRepositoryImpl>(), koin.get<BarsRecordbookRepository>())
        assertSame(barsPreferenceBefore, koin.get<BarsPreferenceRepository>())
        assertSame(koin.get<BarsPreferenceRepositoryImpl>(), koin.get<BarsPreferenceRepository>())
        assertSame(marksBefore, koin.get<MarkTrackingRepository>())
        assertSame(koin.get<MarkTrackingRepositoryImpl>(), koin.get<MarkTrackingRepository>())
        assertSame(sheetsBefore, koin.get<SheetScoresRepository>())
        assertSame(koin.get<SheetScoresRepositoryImpl>(), koin.get<SheetScoresRepository>())
        assertSame(bindingsBefore, koin.get<SubjectBindingStore>())
        assertSame(koin.get<DataStoreSubjectBindingStore>(), koin.get<SubjectBindingStore>())
        assertNotSame(Fakes.sport, koin.get<SportScoreRepository>())
        assertSame(koin.get<SportScoreRepositoryImpl>(), koin.get<SportScoreRepository>())
        assertSame(koin.get<SubjectLessonsGatewayImpl>(), koin.get<SubjectLessonsGateway>())
        assertSame(koin.get<ScheduleRepositoryImpl>(), koin.get<ScheduleRefreshGateway>())
        assertSame(core.academicTimeProvider(), koin.get<AcademicTimeProvider>())
        assertSame(koin.get<SubjectLinksRepositoryImpl>(), koin.get<SubjectLinksRepository>())
        assertSame(koin.get<TeacherLevelsRepositoryImpl>(), koin.get<TeacherLevelsRepository>())
    }

    @Test
    fun `a replaced fixture is left to the host that replaced it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        val first = RecordbookDebugFixtures.load(application, Fakes)
        val second = RecordbookDebugFixtures.load(application, Fakes)
        RecordbookDebugFixtures.unload(application, first)
        assertSame(Fakes.recordbook, koin.get<RecordbookRepository>())

        RecordbookDebugFixtures.unload(application, second)
        assertNotSame(Fakes.recordbook, koin.get<RecordbookRepository>())
    }

    /** Only identities are compared, so the debug host's in-memory stand-ins serve as the fakes. */
    private object Fakes : RecordbookDebugFixtures.Fakes {
        val recordbook = RecordbookPreviewFixtures.Recordbook(RecordbookPreviewFixtures.Phase.MIDDLE)
        val bars = object : BarsRecordbookRepository {
            override suspend fun getSubjects(period: RecordbookPeriod) = AppResult.Success(emptyList<RecordbookSubject>())
            override suspend fun getSubject(journal: BarsJournalReference) = AppResult.Failure(AppError.NotFound)
        }
        val barsPreference = object : BarsPreferenceRepository {
            override suspend fun isEnabled() = false
            override suspend fun setEnabled(enabled: Boolean): AppResult<Unit> = AppResult.Success(Unit)
        }
        val marks = RecordbookPreviewActivity.MemoryMarkTracking
        val sheets = RecordbookPreviewActivity.MemorySheetScores
        val bindings = RecordbookPreviewActivity.MemoryBindings()
        val sport = FakeSportScoreRepository()
        val lessons = FakeSubjectLessonsGateway()
        val scheduleRefresh = FakeScheduleRefreshGateway()
        val links = FakeSubjectLinksRepository()
        val levels = FakeTeacherLevelsRepository()
        val time = FixedAcademicTime()

        override fun recordbook(): RecordbookRepository = recordbook
        override fun bars(): BarsRecordbookRepository = bars
        override fun barsPreference(): BarsPreferenceRepository = barsPreference
        override fun marks(): MarkTrackingRepository = marks
        override fun sheets(): SheetScoresRepository = sheets
        override fun bindings(): SubjectBindingStore = bindings
        override fun sport(): SportScoreRepository = sport
        override fun lessons(): SubjectLessonsGateway = lessons
        override fun scheduleRefresh(): ScheduleRefreshGateway = scheduleRefresh
        override fun links(): SubjectLinksRepository = links
        override fun levels(): TeacherLevelsRepository = levels
        override fun time(): AcademicTimeProvider = time
    }

    /** As in `KoinStartTest`: Robolectric's `onCreate()` stops at `FcmWork.syncToken` after Koin and Hilt are up. */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }
}
