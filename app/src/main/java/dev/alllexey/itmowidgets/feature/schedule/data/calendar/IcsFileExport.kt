package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.javaNow
import dev.alllexey.itmowidgets.core.time.javaToday
import dev.alllexey.itmowidgets.core.time.javaZone
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvents
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.IcsWriter
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * The own schedule of a range as a `.ics` file in `cacheDir/ics`, shared through the app's `FileProvider`. Only the
 * latest file is kept. Events are the same as in calendar synchronization, with the same UIDs.
 */
class IcsFileExport internal constructor(
    private val schedule: OwnScheduleSource,
    private val time: AcademicTimeProvider,
    private val buildings: BuildingDirectory,
    private val directory: File,
    private val dispatchers: AppDispatchers,
    private val uriOf: (File) -> String
) : ScheduleIcsExport {

    @Inject constructor(
        @ApplicationContext context: Context,
        schedule: OwnScheduleSource,
        time: AcademicTimeProvider,
        buildings: BuildingDirectory,
        dispatchers: AppDispatchers
    ) : this(schedule, time, buildings, File(context.cacheDir, DIRECTORY), dispatchers, { file ->
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file).toString()
    })

    override suspend fun export(range: ScheduleExportRange): AppResult<IcsFile?> = try {
        val dates = range.dates(time.javaToday())
        val days = schedule.read(dates.start, dates.endInclusive)
        val events = CalendarEvents.from(days, time.javaZone()) { lesson ->
            buildings.find(lesson.buildingId, lesson.mainBuildingId, lesson.building?.raw)?.address
        }
        if (events.isEmpty()) {
            AppResult.Success(null)
        } else {
            val name = "itmo-schedule-${dates.start}-${dates.endInclusive}.ics"
            val file = withContext(dispatchers.io) {
                check(directory.isDirectory || directory.mkdirs())
                directory.listFiles()?.forEach(File::delete)
                File(directory, name).apply { writeText(IcsWriter.write(events, time.javaNow().toInstant()), Charsets.UTF_8) }
            }
            AppResult.Success(IcsFile(uriOf(file), name, events.size))
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        AppResult.Failure(error.toAppError())
    }

    companion object {
        const val DIRECTORY = "ics"
        /** Matches the provider in the manifest: `${applicationId}.files`. */
        const val AUTHORITY_SUFFIX = ".files"
    }
}
