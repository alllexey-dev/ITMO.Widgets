package dev.alllexey.itmowidgets.feature.recordbook.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class RecordbookModuleTest {

    /**
     * The recordbook data comes from the app's `RecordbookBridge`, the core contracts from `CoreBridge`, links and
     * levels from the resources and reviews bridges, the handle from the platform; the resolvers, the loaders and the
     * ViewModels resolve inside the module.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theRecordbookModuleResolvesWithTheBridgedTypes() {
        recordbookModule.verify(
            extraTypes = listOf(
                RecordbookRepository::class,
                BarsRecordbookRepository::class,
                BarsPreferenceRepository::class,
                BarsSessionRepository::class,
                MarkTrackingRepository::class,
                SheetScoresRepository::class,
                SubjectBindingStore::class,
                SportScoreRepository::class,
                SubjectLessonsGateway::class,
                ScheduleRefreshGateway::class,
                SubjectLinksRepository::class,
                TeacherLevelsRepository::class,
                AcademicTimeProvider::class,
                SavedStateHandle::class,
            )
        )
    }
}
