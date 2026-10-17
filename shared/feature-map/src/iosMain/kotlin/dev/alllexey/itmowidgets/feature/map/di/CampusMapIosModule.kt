package dev.alllexey.itmowidgets.feature.map.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.feature.map.data.mirror.CampusMapEndpoint
import dev.alllexey.itmowidgets.feature.map.presentation.CampusMapViewModel
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle

/**
 * The iOS bindings of [campusMapModule]: the mirror from the bundle's `CampusMapURL` (`CAMPUS_MAP_URL` in
 * `Base.xcconfig`), the production mirror when the key is missing or unexpanded.
 */
val campusMapIosModule: Module = module {
    single { CampusMapEndpoint(campusMapUrl(NSBundle.mainBundle.objectForInfoDictionaryKey(INFO_KEY) as? String)) }
}

internal fun campusMapUrl(value: String?): String = value?.takeIf { it.startsWith("http") } ?: DEFAULT_URL

/** The Koin parameters of the SwiftUI map: what Android's shell seeds from the route. */
fun campusMapParameters(route: AppRoutes.CampusMap): List<Any> = listOf(SavedStateHandle(CampusMapViewModel.argsOf(route.args)))

private const val INFO_KEY = "CampusMapURL"
private const val DEFAULT_URL = "https://map.alllexey.dev/app/v1/"
