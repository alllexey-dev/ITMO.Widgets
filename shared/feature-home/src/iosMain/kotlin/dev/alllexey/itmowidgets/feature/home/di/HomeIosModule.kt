package dev.alllexey.itmowidgets.feature.home.di

import dev.alllexey.itmowidgets.feature.home.data.IosHomeHintStatus
import dev.alllexey.itmowidgets.feature.home.data.PlacedWidgetKinds
import dev.alllexey.itmowidgets.feature.home.data.UserNotificationsAuthorization
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStatus
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The iOS ports of [homeModule], what `:app`'s `HomeBridge` gives Android; load it with that module: the device's
 * [HomeHintStatus] over the app's placed widgets ([widgets], WidgetKit through the Swift app) and
 * `UNUserNotificationCenter`. The feed's other sources and renderers arrive with their features' modules.
 */
fun homeIosModule(widgets: PlacedWidgetKinds): Module = module {
    single<HomeHintStatus> { IosHomeHintStatus(widgets, UserNotificationsAuthorization, get()) }
}
