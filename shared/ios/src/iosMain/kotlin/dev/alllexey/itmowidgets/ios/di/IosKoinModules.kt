package dev.alllexey.itmowidgets.ios.di

import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.ios.IosPlatform
import org.koin.core.module.Module

/**
 * Every Koin module of the iOS app process, what `KoinModules` is on Android. One line per feature: its bindings live
 * in `shared/feature-<x>/src/iosMain/.../di/<Area>IosModule.kt`, and the feature's IO card appends the line.
 */
object IosKoinModules {

    fun all(platform: IosPlatform): List<Module> = listOf(
        iosCoreModule(platform),
    )
}
