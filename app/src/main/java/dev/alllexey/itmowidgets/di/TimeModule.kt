package dev.alllexey.itmowidgets.di

import dev.alllexey.itmoapi.core.MOSCOW
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideStore
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.DefaultAcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.FileAcademicTimeOverrideStore
import java.io.File
import kotlin.time.Clock
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * Time. The provider is keyed by its implementation as well, so a debug fixture that overrides
 * [AcademicTimeProvider] can point the key back at the same instance.
 */
val timeModule = module {
    // The wall clock; AcademicTimeProvider reads it in the academic zone.
    single<Clock> { Clock.System }
    single<AcademicTimeOverrideStore> {
        FileAcademicTimeOverrideStore(
            AtomicTextFile(File(androidContext().noBackupFilesDir, ACADEMIC_TIME_OVERRIDE_FILE))
        )
    }
    single { DefaultAcademicTimeProvider(get(), MOSCOW, get()) } binds
        arrayOf(AcademicTimeProvider::class, AcademicTimeOverrideController::class)
}

private const val ACADEMIC_TIME_OVERRIDE_FILE = "debug/academic_date_override"
