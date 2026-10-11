package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.diagnostics.FileAppDiagnostics
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Application reads the file implementation itself (crash handler, pending crashes). */
val diagnosticsModule = module {
    single { FileAppDiagnostics(androidContext(), get(), get(), get()) } bind AppDiagnostics::class
}
