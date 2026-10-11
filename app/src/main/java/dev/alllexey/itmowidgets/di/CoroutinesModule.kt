package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val coroutinesModule = module {
    single { AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default, main = Dispatchers.Main) }
    // The application scope, for work that must finish after the caller is gone; the only CoroutineScope of the
    // graph, so it needs no qualifier.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + get<AppDispatchers>().default) }
}
