package dev.alllexey.itmowidgets.data.mapper

import api.myitmo.model.sport.ChosenSportSection
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.core.model.SportQueueEntry
import dev.alllexey.itmowidgets.domain.model.sport.SectionName
import dev.alllexey.itmowidgets.domain.model.sport.SportAttempts
import dev.alllexey.itmowidgets.domain.model.sport.SportAttendance
import dev.alllexey.itmowidgets.domain.model.sport.SportBooking
import dev.alllexey.itmowidgets.domain.model.sport.SportLesson
import dev.alllexey.itmowidgets.domain.model.sport.SportScore
import dev.alllexey.itmowidgets.domain.model.sport.UnavailableReason
import java.time.OffsetDateTime

fun api.myitmo.model.sport.SportScore.toModel(): SportScore {
    return SportScore(
        attendances = sum.attendances.toInt(),
        other = sum.other.toInt(),
        attendancesData = attendances.map { it.toModel() }
    )
}

fun api.myitmo.model.sport.SportAttendance.toModel(): SportAttendance {
    return SportAttendance(
        type = type,
        name = name?.let { SectionName(it) },
        evaluationId = evaluationId,
        evaluationName = evaluationName,
        sectionLevel = sectionLevel,
        score = score,
        dateTime = date,
        isCompetition = isCompetition
    )
}

fun api.myitmo.model.sport.SportAttempts.toModel(): SportAttempts {
    return SportAttempts(
        total = totalAttempts,
        used = usedAttempts,
        free = freeAttempts,
        canSignIn = isCanSignIn
    )
}

fun api.myitmo.model.sport.SportLesson.toModel(now: OffsetDateTime): SportLesson {
    val unavailableReasons = UnavailableReason.getSortedUnavailableReasons(this, now)
    return SportLesson(
        isLessonReal = true,
        lessonId = id,
        start = date,
        end = dateEnd,
        sectionId = sectionId,
        sectionName = SectionName(sectionName),
        sectionLevel = sectionLevel.toInt(),
        lessonGroupId = lessonGroupId,
        lessonLevel = lessonLevel.toInt(),
        typeId = typeId.toInt(),
        buildingId = buildingId,
        roomId = roomId,
        roomName = roomName,
        limit = limit.toInt(),
        available = available.toInt(),
        comment = comment,
        timeSlotId = timeSlotId,
        timeSlotStart = timeSlotStart,
        timeSlotEnd = timeSlotEnd,
        intersection = intersection,
        canSignIn = canSignIn.isCanSignIn,
        unavailableReasons = unavailableReasons,
        signed = signed,
        teacherIsu = teacherIsu.toInt(),
        teacherFio = teacherFio,
        signEntry = null,
        signQueue = null,
        friendsBookings = emptyList(),
    )
}


fun ChosenSportSection.toBookings(): List<SportBooking> {
    return lessonGroups.flatMap { group ->
        group.lessons.map { lesson ->
            SportBooking(
                isLessonReal = true,
                lessonId = lesson.id,
                sectionName = SectionName(sectionName),
                start = lesson.dateStart,
                end = lesson.dateEnd,
                roomName = lesson.roomName,
                teacherFio = lesson.teacherFio,
                teacherIsu = lesson.teacherIsu.toInt(),
                sectionLevel = level,
                lessonLevel = group.level,
                signed = true,
                signEntry = null,
                friendsBookings = emptyList()
            )
        }
    }
}

fun SportQueueEntry.toBooking(): SportBooking {
    val (lesson, isReal) = when (this) {
        is SportFreeSignEntry -> this.targetLesson to true
        is SportAutoSignEntry -> (this.realLesson?.to(true) ?: (this.targetLesson to false))
    }

    return SportBooking(
        isLessonReal = isReal,
        lessonId = if (isReal) lesson.id else -lesson.id,
        sectionName = SectionName(lesson.sectionName),
        start = lesson.start.plusDays(if (this is SportAutoSignEntry) 14 else 0),
        end = lesson.end.plusDays(if (this is SportAutoSignEntry) 14 else 0),
        roomName = lesson.roomName,
        teacherFio = lesson.teacherFio,
        teacherIsu = lesson.teacherIsu.toInt(),
        sectionLevel = lesson.sectionLevel.toInt(),
        lessonLevel = lesson.level.toInt(),
        signed = false,
        signEntry = this,
        friendsBookings = emptyList()
    )
}
