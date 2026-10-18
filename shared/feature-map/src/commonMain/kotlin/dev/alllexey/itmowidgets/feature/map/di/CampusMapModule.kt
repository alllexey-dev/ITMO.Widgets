package dev.alllexey.itmowidgets.feature.map.di

import dev.alllexey.itmowidgets.feature.map.data.CampusMapRepositoryImpl
import dev.alllexey.itmowidgets.feature.map.data.mirror.CampusMapApi
import dev.alllexey.itmowidgets.feature.map.data.mirror.CampusMapEndpoint
import dev.alllexey.itmowidgets.feature.map.domain.CampusMapRepository
import dev.alllexey.itmowidgets.feature.map.presentation.CampusMapViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The campus map. The platform modules provide the mirror's [CampusMapEndpoint] and the core types (`AppDirectories`,
 * `DemoMode`, `AppDispatchers`, `BuildingDirectory`, the schedule's `DayLessonsGateway`); the `SavedStateHandle`
 * carries the route's `CampusMapArgs`.
 */
val campusMapModule = module {
    // Two constructors each (the internal ones are the tests' seams), so the public ones' types are named.
    singleOf<CampusMapApi, CampusMapEndpoint, dev.alllexey.itmowidgets.core.demo.DemoMode>(::CampusMapApi)
    singleOf<CampusMapRepositoryImpl, CampusMapApi, dev.alllexey.itmowidgets.core.storage.AppDirectories, dev.alllexey.itmowidgets.core.coroutines.AppDispatchers>(
        ::CampusMapRepositoryImpl,
    ) { bind<CampusMapRepository>() }
    viewModelOf(::CampusMapViewModel)
}
