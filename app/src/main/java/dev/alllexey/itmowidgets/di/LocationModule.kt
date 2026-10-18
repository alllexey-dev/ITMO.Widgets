package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.feature.map.data.mirror.CampusMapEndpoint
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val locationModule = module {
    single {
        val json = androidContext().resources.openRawResource(R.raw.itmo_buildings).bufferedReader().use { it.readText() }
        BuildingDirectory.parse(json)
    }
    // The campus-map mirror; `-PcampusMapUrl=<url>` points a build at a local one (docs/features/map.md).
    single { CampusMapEndpoint(BuildConfig.CAMPUS_MAP_URL) }
}
