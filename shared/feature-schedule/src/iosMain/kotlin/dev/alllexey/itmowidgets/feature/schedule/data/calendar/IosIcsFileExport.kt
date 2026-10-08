package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleFileSystem
import dev.alllexey.itmowidgets.feature.schedule.data.toAppError
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvents
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.IcsWriter
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import platform.Foundation.NSURL

/**
 * The own schedule of a range as a `.ics` file in the app's temporary directory (`tmp/ics`), which the sheet hands
 * to `ShareLink` by its `file://` address (IO-15b). Only the latest file is kept. Events and UIDs are the
 * synchronization's, as in Android's `IcsFileExport`.
 */
class IosIcsFileExport internal constructor(
    private val schedule: OwnScheduleSource,
    private val time: AcademicTimeProvider,
    private val buildings: BuildingDirectory,
    private val dispatchers: AppDispatchers,
    private val directory: Path,
    private val fileSystem: FileSystem,
    private val uriOf: (Path) -> String,
) : ScheduleIcsExport {

    constructor(
        schedule: OwnScheduleSource,
        time: AcademicTimeProvider,
        buildings: BuildingDirectory,
        dispatchers: AppDispatchers,
    ) : this(
        schedule, time, buildings, dispatchers, FileSystem.SYSTEM_TEMPORARY_DIRECTORY / DIRECTORY, ScheduleFileSystem,
        { path -> checkNotNull(NSURL.fileURLWithPath(path.toString()).absoluteString) },
    )

    override suspend fun export(range: ScheduleExportRange): AppResult<IcsFile?> = try {
        val dates = range.dates(time.today())
        val days = schedule.read(dates.start, dates.endInclusive)
        val events = CalendarEvents.from(days, time.timeZone) { lesson ->
            buildings.find(lesson.buildingId, lesson.mainBuildingId, lesson.building?.raw)?.address
        }
        if (events.isEmpty()) {
            AppResult.Success(null)
        } else {
            val name = "itmo-schedule-${dates.start}-${dates.endInclusive}.ics"
            val file = withContext(dispatchers.io) {
                fileSystem.deleteRecursively(directory)
                fileSystem.createDirectories(directory)
                (directory / name).also { path ->
                    fileSystem.write(path) { writeUtf8(IcsWriter.write(events, time.now())) }
                }
            }
            AppResult.Success(IcsFile(uriOf(file), name, events.size))
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        AppResult.Failure(error.toAppError())
    }

    private companion object {
        const val DIRECTORY = "ics"
    }
}
