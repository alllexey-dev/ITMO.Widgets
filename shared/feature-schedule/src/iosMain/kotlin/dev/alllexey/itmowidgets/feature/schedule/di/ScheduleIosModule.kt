package dev.alllexey.itmowidgets.feature.schedule.di

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle

/**
 * The iOS bindings of [scheduleDataModule] and [scheduleModule], what the app's Koin modules give Android; load it with
 * those two modules and `scheduleChangesIosModule`, which binds the change notifier and scheduler. Core 2.0's schedule
 * area comes from the one `BackendClient`; the known buildings of the map hand-off from [buildingsJson], by default the
 * app bundle's `itmo_buildings.json` (the file Android reads from `res/raw`, copied by `iosApp/project.yml`).
 *
 * The phone calendar and the sync's scheduler are `calendarIosModule`'s, the teacher tones `reviewsModule`'s and the
 * pending sport rows `sportModule`'s.
 */
fun scheduleIosModule(buildingsJson: () -> String? = ::bundledBuildings): Module = module {
    single<ScheduleApi> { get<BackendClient>().schedule }
    single { knownBuildings(buildingsJson, get()) }
}

/** A missing or damaged file leaves the map hand-off on the raw building text, as for an unknown building. */
private fun knownBuildings(json: () -> String?, log: AppLog): BuildingDirectory {
    val text = json() ?: return BuildingDirectory(emptyList()).also { log.warn(TAG, "No itmo_buildings.json") }
    return runCatching { BuildingDirectory.parse(text) }
        .onFailure { log.warn(TAG, "Unreadable itmo_buildings.json: ${it.message}") }
        .getOrDefault(BuildingDirectory(emptyList()))
}

private fun bundledBuildings(): String? {
    val path = NSBundle.mainBundle.pathForResource(BUILDINGS_FILE, ofType = BUILDINGS_TYPE) ?: return null
    return runCatching { FileSystem.SYSTEM.read(path.toPath()) { readUtf8() } }.getOrNull()
}

private const val TAG = "ScheduleIos"
private const val BUILDINGS_FILE = "itmo_buildings"
private const val BUILDINGS_TYPE = "json"
