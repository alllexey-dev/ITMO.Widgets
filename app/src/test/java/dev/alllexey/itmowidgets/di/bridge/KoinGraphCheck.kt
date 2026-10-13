package dev.alllexey.itmowidgets.di.bridge

import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonSheetViewModel
import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import dev.alllexey.itmoapi.myitmo.MyItmoConfiguration
import dev.alllexey.itmowidgets.client.AccessTokenSource
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

/**
 * The graph check every Koin module goes through, on the plain JVM.
 *
 * - `verify()` fails on a missing binding or a cycle: it reads the constructor of each definition's type.
 * - Loading every module with `allowOverride(false)` fails on a type defined in two modules.
 * - Koin keeps the last of two definitions of one type inside one module without an error; `DiRulesTest` catches
 *   that in the source.
 */
internal object KoinGraphCheck {

    /** Supplied by the platform, `androidContext()` or the caller (`SavedStateHandle` from `CreationExtras`). */
    private val platformTypes = listOf(
        Context::class,
        Application::class,
        SavedStateHandle::class,
        WorkerParameters::class,
    )

    /** Values the app modules pass to a constructor in place: dispatchers, the zone, files, a clock, flags, names. */
    private val inPlaceTypes = listOf(
        CoroutineDispatcher::class,
        TimeZone::class,
        File::class,
        java.time.Clock::class,
        Boolean::class,
        String::class,
        MyItmoConfiguration::class,
        AccessTokenSource::class,
    )

    /**
     * Definitions whose constructor takes a list the module parses in place or reads as an open set (`getAll()`), or
     * the screen's arguments the caller passes.
     */
    private val inPlaceLists = injectedParameters(
        definition<BuildingDirectory>(List::class),
        definition<LessonSheetViewModel>(List::class),
        definition<RecordbookSubjectViewModel>(RecordbookSubjectArgs::class),
    )

    @OptIn(KoinExperimentalAPI::class)
    fun assertValid(modules: List<Module>) {
        module { includes(modules) }.verify(extraTypes = platformTypes + inPlaceTypes, injections = inPlaceLists)
        koinApplication {
            allowOverride(false)
            modules(modules)
        }.close()
    }
}
