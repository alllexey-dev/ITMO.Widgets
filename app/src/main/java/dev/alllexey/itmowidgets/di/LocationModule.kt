package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val locationModule = module {
    single {
        val json = androidContext().resources.openRawResource(R.raw.itmo_buildings).bufferedReader().use { it.readText() }
        BuildingDirectory.parse(json)
    }
}
