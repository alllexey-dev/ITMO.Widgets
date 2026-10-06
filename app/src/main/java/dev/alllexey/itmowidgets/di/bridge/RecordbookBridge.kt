package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the recordbook data the screens read. Hilt constructs each `@Singleton` that the session cleaners,
 * `MarksWorker`, the home marks card and the BARS sign-in also hold, so they and the Koin ViewModels share one
 * instance (one graph per binding) until KM-11b moves the data into the module.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecordbookBridgeEntryPoint {
    fun recordbookRepository(): RecordbookRepository
    fun barsRecordbookRepository(): BarsRecordbookRepository
    fun barsPreferenceRepository(): BarsPreferenceRepository
    fun barsSessionRepository(): BarsSessionRepository
    fun markTrackingRepository(): MarkTrackingRepository
    fun sheetScoresRepository(): SheetScoresRepository
    fun subjectBindingStore(): SubjectBindingStore

    companion object {
        fun from(context: Context): RecordbookBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, RecordbookBridgeEntryPoint::class.java)
    }
}

/** Lazy singles: Koin starts before Hilt builds its component, so each one reads Hilt on first use. */
val recordbookBridgeModule = module {
    single<RecordbookRepository> { RecordbookBridgeEntryPoint.from(androidContext()).recordbookRepository() }
    single<BarsRecordbookRepository> { RecordbookBridgeEntryPoint.from(androidContext()).barsRecordbookRepository() }
    single<BarsPreferenceRepository> { RecordbookBridgeEntryPoint.from(androidContext()).barsPreferenceRepository() }
    single<BarsSessionRepository> { RecordbookBridgeEntryPoint.from(androidContext()).barsSessionRepository() }
    single<MarkTrackingRepository> { RecordbookBridgeEntryPoint.from(androidContext()).markTrackingRepository() }
    single<SheetScoresRepository> { RecordbookBridgeEntryPoint.from(androidContext()).sheetScoresRepository() }
    single<SubjectBindingStore> { RecordbookBridgeEntryPoint.from(androidContext()).subjectBindingStore() }
}
