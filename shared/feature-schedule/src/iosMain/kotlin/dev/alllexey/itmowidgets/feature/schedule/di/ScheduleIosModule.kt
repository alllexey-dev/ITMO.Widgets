package dev.alllexey.itmowidgets.feature.schedule.di

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.MarkedEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import kotlin.time.Instant
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
 * Until their IO cards ship, the other ports answer without a request and without data: no phone calendar
 * ([UnofferedPhoneCalendars], IO-15b binds EventKit), no teacher tones while iOS does not offer reviews
 * ([UnofferedTeacherLevels], IO-09f loads `reviewsModule`). Each card that ships the real binding removes the
 * stand-in here, since the graph refuses an override; the pending sport rows come from `sportModule` (IO-09c).
 */
fun scheduleIosModule(buildingsJson: () -> String? = ::bundledBuildings): Module = module {
    single<ScheduleApi> { get<BackendClient>().schedule }
    single { knownBuildings(buildingsJson, get()) }
    single<PhoneCalendars> { UnofferedPhoneCalendars }
    single<CalendarSyncScheduler> { UnofferedCalendarSyncScheduler }
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

/**
 * The phone calendar before IO-15b: no access, no calendar of the app, so the sync never turns on
 * (`PlatformCapabilities.calendarExport` also hides its settings rows); a write throws, as a refused call does.
 */
private object UnofferedPhoneCalendars : PhoneCalendars {
    override fun hasAccess(): Boolean = false

    override fun exists(id: Long): Boolean = false

    override fun findOwn(): Long? = null

    override fun createOwn(): Long = unoffered()

    override fun deleteOwn(id: Long) = unoffered()

    override fun insert(calendarId: Long, event: CalendarEvent): Long = unoffered()

    override fun update(eventId: Long, event: CalendarEvent): Boolean = unoffered()

    override fun delete(eventId: Long) = unoffered()

    override fun marked(calendarId: Long, from: Instant, to: Instant): List<MarkedEvent> = emptyList()

    private fun unoffered(): Nothing = throw IllegalStateException("iOS offers no calendar sync yet")
}

/** Nothing to schedule while the sync cannot turn on; the app refresh task is never touched. */
private object UnofferedCalendarSyncScheduler : CalendarSyncScheduler {
    override fun ensurePeriodic() = Unit

    override fun runOnce() = Unit

    override fun cancel() = Unit
}

/** No tone dot next to a teacher while iOS does not offer reviews (`PlatformCapabilities.reviews`, App Review 1.2). */
private object UnofferedTeacherLevels : TeacherLevelsRepository {
    override suspend fun levels(isus: Set<Int>): Map<Int, TeacherLevel> = emptyMap()
}

private const val TAG = "ScheduleIos"
private const val BUILDINGS_FILE = "itmo_buildings"
private const val BUILDINGS_TYPE = "json"
