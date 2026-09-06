package dev.alllexey.itmowidgets.feature.sport.data.mapper

import api.myitmo.model.sport.ChosenSportSection
import dev.alllexey.itmowidgets.core.model.QueueEntryStatus as QueueEntryStatusDto
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry as SportAutoSignEntryDto
import dev.alllexey.itmowidgets.core.model.SportAutoSignLimits as SportAutoSignLimitsDto
import dev.alllexey.itmowidgets.core.model.SportAutoSignQueue as SportAutoSignQueueDto
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry as SportFreeSignEntryDto
import dev.alllexey.itmowidgets.core.model.SportFreeSignQueue as SportFreeSignQueueDto
import dev.alllexey.itmowidgets.core.model.SportLessonDto
import dev.alllexey.itmowidgets.core.model.SportQueue as SportQueueDto
import dev.alllexey.itmowidgets.core.model.SportQueueEntry as SportQueueEntryDto
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttendance
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterOption
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportTimeSlot
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import java.time.OffsetDateTime

fun api.myitmo.model.sport.SportScore.toModel(): SportScore {
    return SportScore(
        attendances = sum.attendances.toInt(),
        other = sum.other.toInt(),
        attendancesData = attendances.orEmpty().map { it.toModel() }
    )
}

fun api.myitmo.model.sport.SportAttendance.toModel(): SportAttendance {
    return SportAttendance(
        type = type.trim(),
        name = name?.trim()?.let(::SectionName),
        evaluationId = evaluationId,
        evaluationName = evaluationName?.trim(),
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
    val unavailableReasons = UnavailableReason.getSortedUnavailableReasons(
        signed = signed == true,
        startsAt = date,
        available = available?.toInt() ?: 0,
        serverReasons = canSignIn?.unavailableReasons.orEmpty(),
        now = now
    )
    return SportLesson(
        isLessonReal = true,
        lessonId = id,
        start = date,
        end = dateEnd,
        sectionId = sectionId,
        sectionName = SectionName(sectionName.trim()),
        sectionLevel = sectionLevel.toInt(),
        lessonGroupId = lessonGroupId,
        lessonLevel = lessonLevel.toInt(),
        typeId = typeId.toInt(),
        buildingId = buildingId,
        roomId = roomId,
        roomName = roomName.trim(),
        limit = limit.toInt(),
        available = available.toInt(),
        comment = comment?.trim(),
        timeSlotId = timeSlotId,
        timeSlotStart = timeSlotStart.trim(),
        timeSlotEnd = timeSlotEnd.trim(),
        intersection = intersection,
        canSignIn = canSignIn.isCanSignIn,
        unavailableReasons = unavailableReasons,
        signed = signed,
        teacherIsu = teacherIsu.toInt(),
        teacherFio = teacherFio.trim(),
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
                sectionName = SectionName(sectionName.trim()),
                start = lesson.dateStart,
                end = lesson.dateEnd,
                roomName = lesson.roomName.trim(),
                teacherFio = lesson.teacherFio.trim(),
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

fun api.myitmo.model.sport.SportFilters.toModel(): SportFilterCatalog {
    return SportFilterCatalog(
        buildings = buildingId.orEmpty().map { SportFilterOption(it.id, it.value.trim()) },
        sections = sectionId.orEmpty().map { SportFilterOption(it.id, it.value.trim()) },
        sportTypes = sportTypeId.orEmpty().map { SportFilterOption(it.id, it.value.trim()) },
        teachers = teacherIsu.orEmpty().map { SportFilterOption(it.id, it.value.trim()) }
    )
}

fun api.myitmo.model.sport.TimeSlot.toModel(): SportTimeSlot {
    return SportTimeSlot(
        id = id,
        start = timeStart.trim(),
        end = timeEnd.trim()
    )
}

fun SportAutoSignLimitsDto.toModel(): SportAutoSignLimits {
    return SportAutoSignLimits(
        limit = limit,
        available = available,
        nextAvailableAt = nextAvailableAt
    )
}

fun SportQueueEntryDto.toModel(): SportQueueEntry {
    return when (this) {
        is SportFreeSignEntryDto -> SportFreeSignEntry(
            id = id,
            lessonId = lessonId,
            position = position,
            total = total,
            isCancelled = isCancelled,
            status = status.toModel(),
            createdAt = createdAt,
            firstNotifiedAt = firstNotifiedAt,
            lastNotifiedAt = lastNotifiedAt,
            cancelledAt = cancelledAt,
            satisfiedAt = satisfiedAt,
            expiredAt = expiredAt,
            notificationAttempts = notificationAttempts,
            maxNotificationAttempts = maxNotificationAttempts,
            targetLesson = targetLesson.toModel(),
            forceSign = forceSign
        )

        is SportAutoSignEntryDto -> SportAutoSignEntry(
            id = id,
            prototypeLessonId = prototypeLessonId,
            realLessonId = realLessonId,
            position = position,
            total = total,
            isCancelled = isCancelled,
            status = status.toModel(),
            createdAt = createdAt,
            firstNotifiedAt = firstNotifiedAt,
            lastNotifiedAt = lastNotifiedAt,
            cancelledAt = cancelledAt,
            satisfiedAt = satisfiedAt,
            expiredAt = expiredAt,
            notificationAttempts = notificationAttempts,
            maxNotificationAttempts = maxNotificationAttempts,
            targetLesson = targetLesson.toModel(),
            realLesson = realLesson?.toModel()
        )
    }
}

fun SportQueueDto.toModel(): SportQueue {
    return when (this) {
        is SportFreeSignQueueDto -> SportFreeSignQueue(
            lessonId = lessonId,
            total = total
        )

        is SportAutoSignQueueDto -> SportAutoSignQueue(
            lessonId = lessonId,
            total = total,
            realLessonId = realLessonId
        )
    }
}

private fun QueueEntryStatusDto.toModel(): SportQueueEntryStatus {
    return SportQueueEntryStatus.valueOf(name)
}

private fun SportLessonDto.toModel(): SportQueueLesson {
    return SportQueueLesson(
        id = id,
        sectionId = sectionId,
        sectionName = sectionName.trim(),
        sectionLevel = sectionLevel,
        level = level,
        typeId = typeId,
        buildingId = buildingId,
        roomName = roomName.trim(),
        start = start,
        end = end,
        timeSlotId = timeSlotId,
        teacherIsu = teacherIsu,
        teacherFio = teacherFio.trim()
    )
}
