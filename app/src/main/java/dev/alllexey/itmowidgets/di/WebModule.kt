package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.web.data.WebSessionDataCleaner
import org.koin.core.qualifier.named
import org.koin.dsl.module

val webModule = module {
    single<SessionDataCleaner>(named("web")) { WebSessionDataCleaner(get(), get()) }
}
