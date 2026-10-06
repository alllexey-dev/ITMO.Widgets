package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
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
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsPreference
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeMarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
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
        val recordbook = RecordbookBridgeEntryPoint.from(application)
        val core = CoreBridgeEntryPoint.from(application)
        assertSame(recordbook.recordbookRepository(), koin.get<RecordbookRepository>())
        assertSame(recordbook.barsRecordbookRepository(), koin.get<BarsRecordbookRepository>())
        assertSame(recordbook.barsPreferenceRepository(), koin.get<BarsPreferenceRepository>())
        assertSame(recordbook.markTrackingRepository(), koin.get<MarkTrackingRepository>())
        assertSame(recordbook.sheetScoresRepository(), koin.get<SheetScoresRepository>())
        assertSame(recordbook.subjectBindingStore(), koin.get<SubjectBindingStore>())
        assertNotSame(Fakes.sport, koin.get<SportScoreRepository>())
        assertSame(core.subjectLessonsGateway(), koin.get<SubjectLessonsGateway>())
        assertSame(core.scheduleRefreshGateway(), koin.get<ScheduleRefreshGateway>())
        assertSame(core.academicTimeProvider(), koin.get<AcademicTimeProvider>())
        assertSame(ResourcesBridgeEntryPoint.from(application).subjectLinksRepository(), koin.get<SubjectLinksRepository>())
        assertSame(ReviewsBridgeEntryPoint.from(application).teacherLevelsRepository(), koin.get<TeacherLevelsRepository>())
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

    private object Fakes : RecordbookDebugFixtures.Fakes {
        val recordbook = FakeRecordbookRepository()
        val bars = FakeBarsRepository()
        val barsPreference = FakeBarsPreference()
        val marks = FakeMarkTrackingRepository()
        val sheets = FakeSheetScoresRepository()
        val bindings = FakeSubjectBindingStore()
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
