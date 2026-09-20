package dev.alllexey.itmowidgets.feature.schedule.ui.details

import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import java.io.Serializable

/** A queued or predicted sport booking as the schedule shows it; times travel as ISO strings. */
data class PendingSportDetailsArgs(
    val lessonId: Long,
    val sectionName: String,
    val autoSign: Boolean,
    val isPrediction: Boolean,
    val start: String,
    val end: String,
    val teacherFio: String,
    val roomName: String
) : Serializable

fun PendingSportBooking.toDetailsArgs() = PendingSportDetailsArgs(
    lessonId = lessonId,
    sectionName = sectionName,
    autoSign = queueKind == PendingSportBooking.QueueKind.AUTO,
    isPrediction = isPrediction,
    start = start.toString(),
    end = end.toString(),
    teacherFio = teacherFio,
    roomName = roomName
)
