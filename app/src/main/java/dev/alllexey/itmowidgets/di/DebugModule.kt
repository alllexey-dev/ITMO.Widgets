package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.core.debug.DebugRefreshTokenController
import dev.alllexey.itmowidgets.core.debug.DefaultDebugRefreshTokenController
import dev.alllexey.itmowidgets.core.debug.DefaultSportLessonTemplateController
import dev.alllexey.itmowidgets.core.debug.DefaultSportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.debug.FileSportLessonTemplateStore
import dev.alllexey.itmowidgets.core.debug.FileSportScoreOverrideStore
import dev.alllexey.itmowidgets.core.debug.SportLessonTemplateController
import dev.alllexey.itmowidgets.core.debug.SportLessonTemplateStore
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideController
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideStore
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.debug.presentation.DebugToolsViewModel
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.binds
import org.koin.dsl.module

/** The debug tools' overrides and checks and their ViewModel; the files live in `noBackupFilesDir/debug`. */
val debugModule = module {
    single<SportScoreOverrideStore> {
        FileSportScoreOverrideStore(File(androidContext().noBackupFilesDir, SPORT_SCORE_OVERRIDE_FILE))
    }
    singleOf(::DefaultSportScoreOverrideProvider) binds
        arrayOf(SportScoreOverrideProvider::class, SportScoreOverrideController::class)
    single<SportLessonTemplateStore> {
        FileSportLessonTemplateStore(File(androidContext().noBackupFilesDir, SPORT_LESSON_TEMPLATE_FILE))
    }
    singleOf(::DefaultSportLessonTemplateController) binds arrayOf(SportLessonTemplateController::class)
    single<DebugRefreshTokenController> {
        DefaultDebugRefreshTokenController(
            tokenStore = get(),
            myItmo = get(),
            dataCleaners = getAll<SessionDataCleaner>().toSet(),
            demo = get(),
            dispatchers = get()
        )
    }
    viewModelOf(::DebugToolsViewModel)
}

private const val SPORT_SCORE_OVERRIDE_FILE = "debug/sport_score_override"
private const val SPORT_LESSON_TEMPLATE_FILE = "debug/sport_lesson_templates"
