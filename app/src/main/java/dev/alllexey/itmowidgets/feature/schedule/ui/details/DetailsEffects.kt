package dev.alllexey.itmowidgets.feature.schedule.ui.details

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.ui.navigation.MapLauncher
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsViewModel
import dev.alllexey.itmowidgets.feature.schedule.ui.shortTitle

/**
 * The [LessonDetailsViewModel] arguments of this lesson: its occurrence and, when it is a valid ISU, its teacher.
 */
internal fun LessonDetailsArgs.viewModelArgs(): Bundle = Bundle().apply {
    putLong(LessonDetailsViewModel.ARG_PAIR_ID, pairId)
    putString(LessonDetailsViewModel.ARG_DATE, date)
    UserScreenArgs.profileIsu(teacherIsu)?.let { putInt(LessonDetailsViewModel.ARG_TEACHER_ISU, it) }
}

/**
 * Where the map hand-off of [lesson] points: the directory's building, else the schedule's own building text; null
 * when the lesson names no building.
 */
internal fun BuildingDirectory.lessonMapDestination(lesson: LessonDetailsArgs, context: Context): MapDestination? {
    find(lesson.buildingId, lesson.mainBuildingId, lesson.building)?.let { return it.toMapDestination() }
    val building = lesson.building ?: return null
    return MapDestination(label = Building(building).shortTitle(context), address = building)
}

/** Opens [destination] in a `geo:` handler; without one a snackbar on [anchor] says so. */
internal fun Context.openLessonMap(destination: MapDestination, anchor: View) {
    if (!MapLauncher.open(this, destination)) {
        Snackbar.make(anchor, R.string.schedule_map_unavailable, Snackbar.LENGTH_SHORT).show()
    }
}

/** Opens the lesson's meeting [url]; without a handler a snackbar on [anchor] says so. */
internal fun Context.openLessonLink(url: String, anchor: View) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Snackbar.make(anchor, R.string.link_open_failed, Snackbar.LENGTH_SHORT).show()
    }
}

/** Opens the room of [booking] in a `geo:` handler; without one a snackbar on [anchor] says so. */
internal fun Context.openPendingSportMap(booking: PendingSportDetailsArgs, anchor: View) {
    val destination = MapDestination(label = booking.sectionName, address = booking.roomName)
    if (!MapLauncher.open(this, destination)) {
        Snackbar.make(anchor, R.string.schedule_map_unavailable, Snackbar.LENGTH_SHORT).show()
    }
}
