package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import org.koin.dsl.module

val linksModule = module {
    factory { ShareLinkFactory(BuildConfig.WIDGETS_BASE_URL) }
}
