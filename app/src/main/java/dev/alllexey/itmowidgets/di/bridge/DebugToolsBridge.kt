package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.debug.BarsSessionProbe
import dev.alllexey.itmowidgets.core.debug.DebugRefreshTokenController
import dev.alllexey.itmowidgets.core.debug.SportLessonTemplateController
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.feature.debug.presentation.DebugToolsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * Hilt to Koin for the debug tools' overrides and checks, which Hilt keeps constructing (`di/DebugModule`,
 * `di/TimeModule`, `di/RecordbookModule`; one graph per binding). The time, the opt-in and the background checks come
 * from Koin already.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugToolsBridgeEntryPoint {
    fun academicTimeOverrideController(): AcademicTimeOverrideController
    fun sportScoreOverrideController(): SportScoreOverrideController
    fun sportLessonTemplateController(): SportLessonTemplateController
    fun debugRefreshTokenController(): DebugRefreshTokenController
    fun barsSessionProbe(): BarsSessionProbe

    companion object {
        fun from(context: Context): DebugToolsBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, DebugToolsBridgeEntryPoint::class.java)
    }
}

/** Lazy singles: Koin starts before Hilt builds its component, so each one reads Hilt on first use. */
val debugToolsBridgeModule = module {
    single<AcademicTimeOverrideController> { hilt().academicTimeOverrideController() }
    single<SportScoreOverrideController> { hilt().sportScoreOverrideController() }
    single<SportLessonTemplateController> { hilt().sportLessonTemplateController() }
    single<DebugRefreshTokenController> { hilt().debugRefreshTokenController() }
    single<BarsSessionProbe> { hilt().barsSessionProbe() }
}

private fun Scope.hilt(): DebugToolsBridgeEntryPoint = DebugToolsBridgeEntryPoint.from(androidContext())

/**
 * The debug tools' ViewModel, which both the overlay Fragment and the Compose shell's entry obtain from Koin
 * (`DebugToolsViewModel` was Hilt's until SH-1b8, which a Navigation 3 entry cannot create).
 */
val debugToolsModule = module {
    viewModelOf(::DebugToolsViewModel)
}
