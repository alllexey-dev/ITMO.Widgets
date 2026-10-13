package dev.alllexey.itmowidgets.feature.schedule.domain.changes

import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange

/** The lesson sheet of the slot a change leads to: the new one, or the old one of a cancelled lesson. */
fun ScheduleChange.toLessonDetailsArgs(): LessonDetailsArgs {
    val slot = checkNotNull(after ?: before)
    return LessonDetailsArgs(
        pairId = slot.pairId,
        date = slot.date.toString(),
        subjectName = subjectName,
        typeId = typeId,
        format = slot.format.orEmpty(),
        start = slot.start.toString(),
        end = slot.end.toString(),
        teacherFio = slot.teacherName,
        teacherIsu = slot.teacherIsu,
        room = slot.room,
        building = slot.building,
        buildingId = null,
        mainBuildingId = null,
        note = null,
        zoomUrl = null,
        zoomPassword = null,
        zoomInfo = null,
        flowName = flowName,
    )
}
