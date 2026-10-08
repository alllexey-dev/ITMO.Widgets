package dev.alllexey.itmowidgets.feature.schedule.di

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle

/**
 * The iOS ports of [scheduleDataModule] and [scheduleModule] (IO-09b), what `:app`'s `ScheduleBridge`, `CoreBridge`
 * and `ReviewsBridge` give Android; load it with those two modules and `scheduleChangesIosModule`, which binds the
 * change notifier and scheduler. Core 2.0's schedule area comes from the one `BackendClient`; the known buildings of
 * the map hand-off from [buildingsJson], by default the app bundle's `itmo_buildings.json` (the file Android reads
 * from `res/raw`, copied by `iosApp/project.yml`).
 *
 * The phone calendar and the sync's scheduler are `calendarIosModule`'s (IO-15b). Until their IO cards ship, the
 * other ports answer without a request and without data: no teacher tones while iOS does not offer reviews
 * ([UnofferedTeacherLevels], IO-09f loads `reviewsModule`). Each card that ships the real binding removes the
 * stand-in here, since the graph refuses an override; the pending sport rows come from `sportModule` (IO-09c).
 */
fun scheduleIosModule(buildingsJson: () -> String? = ::bundledBuildings): Module = module {
    single<ScheduleApi> { get<BackendClient>().schedule }
    single { knownBuildings(buildingsJson, get()) }
    single<TeacherLevelsRepository> { UnofferedTeacherLevels }
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

/** No tone dot next to a teacher while iOS does not offer reviews (`PlatformCapabilities.reviews`, App Review 1.2). */
private object UnofferedTeacherLevels : TeacherLevelsRepository {
    override suspend fun levels(isus: Set<Int>): Map<Int, TeacherLevel> = emptyMap()
}

private const val TAG = "ScheduleIos"
private const val BUILDINGS_FILE = "itmo_buildings"
private const val BUILDINGS_TYPE = "json"
